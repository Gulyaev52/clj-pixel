(ns pixel-art.export
  (:require ["./jszip$default" :as jszip]
            [pixel-art.canvas :as canvas]
            [pixel-art.model.sprite :as sprite]
            [re-frame.core :as re-frame]
            [sc.api]
            ["./gif$default" :as create-gif]
            [clojure.string :as string]))

(def max-scale 32)
(def min-scale 1)

(defn adjust-columns-if-need [columns frames]
  (min (max 1 columns) (count frames)))

(defn calc-export-rows [columns frames]
  (. js/Math (floor (/ (count frames) columns))))

(defn get-common-settings-res [db]
  (let [common-settings (-> db :export :common-settings)
        sprite-size (-> db :sprite sprite/get-size)
        frame-size (update-vals sprite-size #(. js/Math (round (* (:scale common-settings) %))))]
    (assoc common-settings :frame-size frame-size)))

(defn get-spritesheet-settings [db]
  (let [common-settings (get-common-settings-res db)
        spritesheet-settings (-> db :export :spritesheet-settings)
        columns (:columns spritesheet-settings)
        frames (-> db :sprite :frames)
        rows (calc-export-rows columns frames)
        spritesheet-size {:width (* (-> common-settings :frame-size :width) columns)
                          :height (* (-> common-settings :frame-size :height) rows)}]
    (-> spritesheet-settings
        (assoc :rows rows :spritesheet-size spritesheet-size)
        (merge common-settings))))

(defn get-image-settings [db]
  (let [common-settings (get-common-settings-res db)]
    (merge common-settings (-> db :export :image-settings))))

(defn init []
  {:opened false
   :current-tab :image
   :common-settings
   {:frames :all
    :layers {:type :visible}
    :direction :forward
    :file-name "untitled"
    :file-type :png
    :scale max-scale}
   :image-settings
   {:repeat true ;; todo: only when gif
    :split-layers false}
   :spritesheet-settings
   {:columns nil} ;; this field is calculated on [::set-opened true] because it depends of actual frames number
   :exporting false})

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (cond-> db
          true (assoc-in [:export :opened] opened)
          opened (update-in [:export :spritesheet-settings :columns]
                            #(adjust-columns-if-need % (:sprite db))))}))

(re-frame/reg-event-fx
 ::select-tab
 (fn [{:keys [db]} [_ tab]]
   {:db (cond-> db
          true (assoc-in [:export :current-tab] tab)
          (= tab :spritesheet) (update-in [:export :common-settings :file-type]
                                          #(if (= % :gif) :png %)))}))

(re-frame/reg-event-fx
 ::set-common-settings-option
 (fn [{:keys [db]} [_ option-key value]]
   (let [updated-db
         (if (or (= option-key :frame-size-width)
                 (= option-key :frame-size-height))
           (let [sprite-size (-> db :sprite sprite/get-size)
                 new-scale (/ value ((if (= option-key :frame-size-width) :width :height)
                                     sprite-size))]
             (assoc-in db [:export :common-settings :scale] new-scale))
           (assoc-in db [:export :common-settings option-key] value))]
     {:db updated-db})))

(re-frame/reg-event-fx
 ::set-image-settings-option
 (fn [{:keys [db]} [_ option-key value]]
   {:db (assoc-in db [:export :image-settings option-key] value)}))

(re-frame/reg-event-fx
 ::set-spritesheet-settings-columns
 (fn [{:keys [db]} [_ value]]
   {:db (assoc-in db
                  [:export :spritesheet-settings :columns]
                  (adjust-columns-if-need value (:sprite db)))}))

(re-frame/reg-event-fx
 ::export
 (fn [{:keys [db]}]
   (let [{:keys [sprite export]} db
         settings (if (= (:current-tab export) :image) ;; todo: use as key
                    (get-image-settings db)
                    (get-spritesheet-settings db))]
     {:db (assoc-in db [:export :exporting] true)
      :fx [[(if (= (-> db :export :current-tab) :image)
              ::export-image
              ::export-spritesheet)
            {:settings settings :sprite sprite}]]})))

(re-frame/reg-event-fx
 ::set-exporting
 (fn [{:keys [db]} [_ exporting]]
   {:db (assoc-in db [:export :exporting] exporting)}))

(defn get-cels-for-rendering [settings sprite]
  (let [cels (sprite/get-cels-with-layers-and-pos sprite)
        selected-poses (sprite/get-selected-cels-pos sprite)]
    (->> (if (= (:frames settings) :selected)
           (->> (sort-by :frame-idx (map :frame-idx selected-poses))
                (map #(nth cels %)))
           cels)
         ((fn [cels]
            (case (-> settings :layers :type)
              :visible
              (map (fn [frame-cels] (filter #(-> % :layer :visibile?) frame-cels)) cels)

              :selected
              (let [selected-layers-idx (set (map :layer-idx selected-poses))]
                (map (fn [frame-cels] (map #(nth frame-cels %) selected-layers-idx)) cels))

              :layer
              (map (fn [frame-cels] [(nth frame-cels (-> settings :layers :idx))]) cels))))
         (#(if (:split-layers settings)
             (map vector (flatten %))
             %))
         (#(if (= (:direction settings) :backwards)
             (reverse %)
             %)))))

(defn scale-canvas [size new-size canvas]
  (if (= size new-size)
    canvas
    (let [new-canvas (canvas/create-canvas new-size)
          new-canvas-ctx (. new-canvas (getContext "2d"))]
      (set! (. new-canvas-ctx -imageSmoothingEnabled) false)
      (. new-canvas-ctx (drawImage canvas 0 0 (:width size) (:height size) 0 0 (:width new-size) (:height new-size)))
      new-canvas)))

(defn download-file [file-name content-blob]
  (let [link (.createElement js/document "a")]
    (set! (.-href link) (.createObjectURL js/URL content-blob))
    (.setAttribute link "download" file-name)
    (.appendChild (.-body js/document) link)
    (.click link)
    (.removeChild (.-body js/document) link)))

;; todo: rename
(defn merge-canvases [new-canvas-size spritesheet-size columns canvases]
  (let [canvas-rows (remove empty? (split-at columns canvases)) ;; todo: wtf?
        new-canvas (canvas/create-canvas spritesheet-size)
        new-canvas-ctx (. new-canvas (getContext "2d"))]
    (doseq [[row-idx row] (map-indexed vector canvas-rows)
            [column-idx canvas] (map-indexed vector row)]
      (. new-canvas-ctx (drawImage canvas
                                   0 0
                                   (:width new-canvas-size) (:height new-canvas-size)
                                   (* column-idx (:width new-canvas-size))
                                   (* row-idx (:height new-canvas-size))
                                   (:width new-canvas-size)
                                   (:height new-canvas-size))))
    new-canvas))

(re-frame/reg-fx
 ::export-spritesheet
 (fn [{:keys [settings sprite]}]
   (let [size (sprite/get-size sprite)
         frame-size (:frame-size settings)
         canvas-frames
         (->> (get-cels-for-rendering settings sprite)
              (map (fn [cels]
                    ;;  todo: нужен ли этот пайплайн? исп scale, transform
                     (->> (canvas/create-canvas size)
                          (canvas/draw-cels-on-single-canvas cels)
                          (scale-canvas size frame-size)))))
         res-canvas (merge-canvases (:spritesheet-size settings) frame-size (:columns settings) canvas-frames)]
     (. res-canvas (toBlob (fn [blob]
                             (download-file (:file-name settings) blob)
                             (re-frame/dispatch [::set-exporting false])))))))

(re-frame/reg-fx
 ::export-image
 (fn [{:keys [settings sprite]}]
   (let [size (sprite/get-size sprite)
         frame-size (:frame-size settings)
         rendered-frames
         (->> (get-cels-for-rendering settings sprite)
              (map (fn [cels]
                    ;;  todo: нужен ли этот пайплайн? исп scale, transform
                     {:canvas (->> (canvas/create-canvas size)
                                   (canvas/draw-cels-on-single-canvas cels)
                                   (scale-canvas size frame-size))
                      :cels cels})))]
     (case (:file-type settings)
       :png (let [files-desc
                  (->> rendered-frames
                       (map (fn [{:keys [canvas cels]}]
                              {:canvas canvas
                               :file-content (canvas/get-base64-from-canvas canvas "png") ;; todo: use arraybuffer?
                               :file-name (let [cel (first cels)
                                                frame-idx (-> cel :pos :frame-idx inc)]
                                            (if (:split-layers settings)
                                              (let [layer-name (string/replace (-> cel :layer :name) #"\s+" "_")]
                                                (str (:file-name settings) "_" frame-idx "_" layer-name ".png"))
                                              (str (:file-name settings) "_" frame-idx ".png")))})))]
              (if (= (count files-desc) 1)
                (let [file-desc (first files-desc)]
                  (. (:canvas file-desc)
                     (toBlob (fn [blob]
                               (download-file (:file-name file-desc) blob)
                               (re-frame/dispatch [::set-exporting false])))))
                (let [zip (jszip)]
                  (doseq [{:keys [file-name file-content]} files-desc]
                    (. zip (file file-name file-content #js {"base64" true})))
                  (.. zip
                      (generateAsync #js {"type" "blob"})
                      (then (fn [blob]
                              (download-file "new_pixel.zip" blob)
                              (re-frame/dispatch [::set-exporting false])))))))


       :gif (create-gif (clj->js (map :canvas rendered-frames))
                        (fn [blob]
                          (download-file (str (:file-name settings) ".gif") blob)
                          (re-frame/dispatch [::set-exporting false])))))))

;; баги
;; 9) fps and frame duration
;; 4) preview
;; 1) чёрный цвет в гифке
;; 11) мб скейла достаточно

;; 13) тесты
;; 14) отрефакторить
;; 5) избавиться от повторения упоминаний :image, :spritesheet(кнопки, сеттинги)
;; 7) поля формы мёржатся при этом экшены раздельные
;; 12) исп cond-> или as->
;; 13) export size export resulotion