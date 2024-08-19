(ns pixel-art.export
  (:require ["./gif$default" :as create-gif]
            ["./jszip$default" :as jszip]
            [clojure.string :as string]
            [pixel-art.canvas :as canvas]
            [pixel-art.model.sprite :as sprite]
            [re-frame.core :as re-frame]
            [sc.api]))

(def max-scale 32)
(def min-scale 1)

(defn init []
  {:opened false
   :current-tab :image
   :preview nil
   :preview-generation false
   :common-settings
   {:frames :all
    :layers {:type :visible}
    :direction :forward
    :file-name "untitled"
    :file-type :png
    :scale min-scale}
   :image-settings
   {:repeat true ;; todo: only when gif
    :split-layers false}
   :spritesheet-settings
   {:columns 1}
   :exporting false})

(defn adjust-columns-if-need [columns frames]
  (min (max 1 columns) (count frames)))

(defn calc-export-rows [columns frames]
  (count (partition-all columns frames)))

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

(defn get-cels-for-rendering [settings sprite]
  (let [cels (sprite/get-denormalized-cels sprite)
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

;; todo: rename
(defn merge-canvases [canvas-size spritesheet-size columns canvases]
  (let [canvas-rows (partition-all columns canvases)
        spritesheet-canvas (canvas/create-canvas spritesheet-size)
        spritesheet-canvas-ctx (. spritesheet-canvas (getContext "2d"))]
    (doseq [[row-idx row] (map-indexed vector canvas-rows)
            [column-idx canvas] (map-indexed vector row)]
      (. spritesheet-canvas-ctx (drawImage canvas
                                           0 0
                                           (:width canvas-size) (:height canvas-size)
                                           (* column-idx (:width canvas-size))
                                           (* row-idx (:height canvas-size))
                                           (:width canvas-size)
                                           (:height canvas-size))))
    spritesheet-canvas))

(defn generate-preview [db]
  (let [db (assoc-in db [:export :preview-generation] true)
        {:keys [sprite export]} db]
    (case (:current-tab export)
      :spritesheet
      (let [settings (get-spritesheet-settings db)
            size (sprite/get-size sprite)
            spritesheet-size {:width (* (:width size) (:columns settings))
                              :height (* (:width size) (:rows settings))} ;; todo: размеры разные
            canvas-frames
            (->> (get-cels-for-rendering settings sprite)
                 (map (fn [cels]
                        (->> (canvas/create-canvas size)
                             (canvas/draw-cels-on-single-canvas cels) ;; убираем scale
                             ))))
            res-canvas (merge-canvases size spritesheet-size (:columns settings) canvas-frames)
            preview-image (canvas/to-data-url res-canvas "png") ;; todo: нам не нужно генерить blob. мб и там генерить base64. это зависит от того как будет работать с большим кол-вом ?
            ]
        {:db (-> db
                 (assoc-in [:export :preview] [preview-image])
                 (assoc-in [:export :preview-generation] false))})

      :image
      (let [settings (get-image-settings db)
            size (sprite/get-size sprite)
            rendered-frames
            (->> (get-cels-for-rendering settings sprite)
                 (map (fn [cels]
                                       ;;  todo: нужен ли этот пайплайн? исп scale, transform
                        {:canvas (->> (canvas/create-canvas size)
                                      (canvas/draw-cels-on-single-canvas cels) ;; убираем scale
                                      )
                         :cels cels})))]
        (case (:file-type settings)
          :png
          {:db (-> db
                   (assoc-in [:export :preview] (map #(canvas/to-data-url (:canvas %) "png") rendered-frames))
                   (assoc-in [:export :preview-generation] false))}

          :gif
          {:db db
           :fx [[::generate-gif {:rendered-frames rendered-frames
                                 :repeat (:repeat settings)
                                 :base64 true
                                 :on-finish [::generate-gif-preview-success] ;; todo: fix
                                 }]]})))))

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   (if opened
     (-> db
         (assoc-in [:export :opened] opened)
         (update-in [:export :spritesheet-settings :columns]
                    #(adjust-columns-if-need % (-> db :sprite :frames)))
         generate-preview)
     {:db (assoc-in db [:export :opened] opened)})))

(re-frame/reg-event-fx
 ::select-tab
 (fn [{:keys [db]} [_ tab]]
   (-> (cond-> db
         true (assoc-in [:export :current-tab] tab)
         (= tab :spritesheet) (update-in [:export :common-settings :file-type]
                                         #(if (= % :gif) :png %)))
       generate-preview)))

(re-frame/reg-event-fx
 ::set-common-settings-option
 (fn [{:keys [db]} [_ option-key value]]
   ;; generate-preview. не имеет смысла запускать на scale, frame-size, file
   (let [updated-db
         (if (or (= option-key :frame-size-width)
                 (= option-key :frame-size-height))
           (let [sprite-size (-> db :sprite sprite/get-size)
                 new-scale (/ value ((if (= option-key :frame-size-width) :width :height)
                                     sprite-size))]
             (assoc-in db [:export :common-settings :scale] new-scale))
           (assoc-in db [:export :common-settings option-key] value))]
     (generate-preview updated-db))))

(re-frame/reg-event-fx
 ::set-image-settings-option
 (fn [{:keys [db]} [_ option-key value]]
   ;; generate-preview. не имеет смысла запускать на scale, frame-size, file
   (-> db
       (assoc-in [:export :image-settings option-key] value)
       generate-preview)))

(re-frame/reg-event-fx
 ::set-spritesheet-settings-columns
 (fn [{:keys [db]} [_ value]]
   ;; generate-preview. не имеет смысла запускать на scale, frame-size, file
   (-> db
       (assoc-in [:export :spritesheet-settings :columns]
                 (adjust-columns-if-need value (-> db :sprite :frames)))
       generate-preview)))

(re-frame/reg-event-fx
 ::set-exporting
 (fn [{:keys [db]} [_ exporting]]
   {:db (assoc-in db [:export :exporting] exporting)}))

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

;; todo: remove
(re-frame/reg-fx
 ::download-file
 (fn [{:keys [file-name file-content]}]
   (println file-name file-content)
   (download-file file-name file-content)))

(re-frame/reg-event-fx
 ::download-generated-blob
 (fn [{:keys [db]} [_ file-name blob]]
   {:db (assoc-in db [:export :exporting] false)
    :fx [[::download-file {:file-name file-name :file-content blob}]]}))

(re-frame/reg-event-fx
 ::generate-gif-preview-success
 (fn [{:keys [db]} [_ gif]]
   {:db (-> db
            (assoc-in [:export :preview] [gif])
            (assoc-in [:export :preview-generation] false))}))

(re-frame/reg-event-fx
 ::export
 (fn [{:keys [db]}]
   (let [db (assoc-in db [:export :exporting] true)
         {:keys [sprite export]} db]
     (case (:current-tab export)
       :spritesheet
       (let [settings (get-spritesheet-settings db)
             size (sprite/get-size sprite)
             frame-size (:frame-size settings)
             canvas-frames
             (->> (get-cels-for-rendering settings sprite)
                  (map (fn [cels]
                                 ;;  todo: нужен ли этот пайплайн? исп scale, transform
                         (->> (canvas/create-canvas size)
                              (canvas/draw-cels-on-single-canvas cels)
                              (scale-canvas size frame-size)))))
             res-canvas (merge-canvases frame-size (:spritesheet-size settings) (:columns settings) canvas-frames)]
         {:db db
          :fx [[::generate-plain-image {:canvas res-canvas
                                        :on-finish [::download-generated-blob (:file-name settings)]}]]})

       :image
       (let [settings (get-image-settings db)
             size (sprite/get-size sprite)
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
           :png
           (if (= (count rendered-frames) 1)
             {:db db
              :fx [[::generate-plain-image {:canvas (:canvas (first rendered-frames))
                                            :on-finish [::download-generated-blob (:file-name settings)]}]]}
             (let [files-desc
                   (->> rendered-frames
                        (map (fn [{:keys [canvas cels]}]
                               {:file-content (canvas/to-base64 canvas "png") ;; todo: use arraybuffer?
                                :file-name (let [cel (first cels)
                                                 frame-idx (-> cel :pos :frame-idx inc)]
                                             (if (:split-layers settings)
                                               (let [layer-name (string/replace (-> cel :layer :name) #"\s+" "_")]
                                                 (str (:file-name settings) "_" frame-idx "_" layer-name ".png"))
                                               (str (:file-name settings) "_" frame-idx ".png")))})))]
               {:db db
                :fx [[::generate-zip {:files-desc files-desc
                                      :on-finish [::download-generated-blob (:file-name settings)]}]]}))

           :gif
           {:db db
            :fx [[::generate-gif {:rendered-frames rendered-frames
                                  :repeat (:repeat settings)
                                  :on-finish [::download-generated-blob (:file-name settings)] ;; todo: fix
                                  }]]}))))))

(defn canvas->blob-promise [canvas]
  (js/Promise. (fn [resolve] (. canvas (toBlob resolve)))))

(re-frame/reg-fx
 ::generate-zip
 (fn [{:keys [files-desc on-finish]}]
   (let [zip (jszip)]
     (doseq [{:keys [file-name file-content]} files-desc]
       (. zip (file file-name file-content #js {"base64" true})))
     (.. zip
         (generateAsync #js {"type" "blob"})
         (then (fn [blob]
                 (re-frame/dispatch (conj on-finish blob))))))))

(re-frame/reg-fx
 ::generate-plain-image ;; png, jpg and so on. todo: rename? 
 (fn [{:keys [canvas on-finish]}]
   (.. (canvas->blob-promise canvas)
       (then (fn [images]
               (re-frame/dispatch (conj on-finish images)))))))

(re-frame/reg-fx
 ::generate-plain-images ;; png, jpg and so on. todo: rename? 
 (fn [{:keys [canvases on-finish]}]
   (.. js/Promise
       (all (map canvas->blob-promise canvases))
       (then (fn [images]
               (re-frame/dispatch (conj on-finish images)))))))

(defn blob->base64 [blob]
  (js/Promise.
   (fn [resolve]
     (let [reader (js/FileReader.)]
       (. reader (readAsDataURL blob))
       (set! (. reader -onloadend)
             (fn [] (resolve (. reader -result))))))))

(re-frame/reg-fx
 ::generate-gif
 (fn [{:keys [rendered-frames base64 repeat on-finish]}]
   (let [gif (create-gif (clj->js {"workers" 2
                                   "quality" 1
                                   "transparent" "rgba(0,0,0,0)"
                                   "background" "#000"
                                   "repeat" (if repeat 0 -1)}))]
     (doseq [{:keys [canvas cels]} rendered-frames]
       (let [cel (first cels)] ;; todo: refactor?
         (. gif (addFrame canvas #js {"delay" (-> cel :frame :duration)}))))
     (. gif (on "finished" (fn [blob]
                             (if base64
                               (.. (blob->base64 blob)
                                   (then (fn [blob-as-base64]
                                           (re-frame/dispatch (conj on-finish blob-as-base64)))))
                               (re-frame/dispatch (conj on-finish blob))))))
     (. gif (render)))))

;; баги
;; 1) чёрный цвет в гифке
;; 14) в spritesheet считать строки только по выбранным

;; 11) мб скейла достаточно
;; 12) исп blob vs base64. нужно смотреть большое кол-во больших элементов
;; 13) оптимизация для слинкованых ячеек
;; 15) цвет фона
;; 16) preview obj
;; 17) generate-preview не имеет смысл всегда запускать
;; 13) тесты
;; 14) отрефакторить
;; 5) избавиться от повторения упоминаний :image, :spritesheet(кнопки, сеттинги)
;; 7) поля формы мёржатся при этом экшены раздельные
;; 12) исп cond-> или as->
;; 13) export size export resulotion
;; 15) переименовать import-export помдуль