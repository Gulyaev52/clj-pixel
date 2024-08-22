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
   :preview {:data [] ;; coll of data-urls when :current-tab=:image; data-url when :current-tab=:spritesheet;
             :generation false}
   :common-settings
   {:frames :all
    :layers {:type :visible}
    :direction :forward
    :file-name "untitled"
    :file-type :png
    :scale min-scale
    :split-layers false}
   :image-settings
   {:repeat true ;; only when gif
    }
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
        scaled-frame-size (update-vals sprite-size #(. js/Math (round (* (:scale common-settings) %))))]
    (assoc common-settings :scaled-frame-size scaled-frame-size)))

(defn get-spritesheet-settings [db]
  (let [common-settings (get-common-settings-res db)
        spritesheet-settings (-> db :export :spritesheet-settings)
        columns (:columns spritesheet-settings)
        frames (-> db :sprite :frames)
        rows (calc-export-rows columns frames)]
    (-> spritesheet-settings
        (assoc :rows rows)
        (merge common-settings))))

(defn get-image-settings [db]
  (let [common-settings (get-common-settings-res db)]
    (merge common-settings (-> db :export :image-settings))))

(defn get-cels-for-rendering [settings sprite]
  (let [selected-poses (sprite/get-selected-cels-pos sprite)]
    (as-> (sprite/get-denormalized-cels sprite) $
      (if (= (:frames settings) :selected)
        (->> (sort-by :frame-idx (map :frame-idx selected-poses))
             (map #(nth $ %)))
        $)

      (case (-> settings :layers :type)
        :visible
        (map (fn [frame-cels] (filter #(-> % :layer :visibile?) frame-cels)) $)

        :selected
        (let [selected-layers-idx (set (map :layer-idx selected-poses))]
          (map (fn [frame-cels] (map #(nth frame-cels %) selected-layers-idx)) $))

        :layer
        (map (fn [frame-cels] [(nth frame-cels (-> settings :layers :idx))]) $))

      (if (:split-layers settings)
        (map vector (flatten $))
        $)

      (if (= (:direction settings) :backwards)
        (reverse $)
        $))))

(defn generate-preview [db]
  (let [db (assoc-in db [:export :preview :generation] true)
        {:keys [sprite export]} db]
    (case (:current-tab export)
      :spritesheet
      (let [settings (get-spritesheet-settings db)
            size (sprite/get-size sprite)
            spritesheet-size {:width (* (:width size) (:columns settings))
                              :height (* (:width size) (:rows settings))}
            img (->> (get-cels-for-rendering settings sprite)
                     (map #(canvas/draw-cels-on-single-canvas % (canvas/create-canvas size)))
                     (canvas/combine size spritesheet-size (:columns settings))
                     (#(canvas/to-data-url % "png")))]
        {:db (assoc-in db [:export :preview] {:data img :generation false})})

      :image
      (let [settings (get-image-settings db)
            size (sprite/get-size sprite)
            rendered-frames
            (->> (get-cels-for-rendering settings sprite)
                 (map (fn [cels]
                        {:canvas (canvas/draw-cels-on-single-canvas cels (canvas/create-canvas size))
                         :cels cels})))]
        (case (:file-type settings)
          :png
          (let [data (map #(canvas/to-data-url (:canvas %) "png") rendered-frames)]
            {:db (assoc-in db [:export :preview] {:data data :generation false})})

          :gif
          {:db db
           :fx [[::generate-gif {:rendered-frames rendered-frames
                                 :repeat (:repeat settings)
                                 :base64 true
                                 :on-finish [::generate-gif-preview-success]}]]})))))

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
 ::set-settings-option
 (fn [{:keys [db]} [_ option-key value]]
   (case option-key
     :columns (-> db
                  (assoc-in [:export :spritesheet-settings option-key]
                            (adjust-columns-if-need value (-> db :sprite :frames)))
                  generate-preview)
     :repeat (-> db
                 (assoc-in [:export :image-settings option-key] value)
                 generate-preview)
     (-> db
         (assoc-in [:export :common-settings option-key] value)
         (#(if-not (some #{option-key} [:file-name :scale])
             (generate-preview %)
             {:db %}))))))

(re-frame/reg-event-fx
 ::export
 (fn [{:keys [db]}]
   (let [db (assoc-in db [:export :exporting] true)
         {:keys [sprite export]} db]
     (case (:current-tab export)
       :spritesheet
       (let [settings (get-spritesheet-settings db)
             size (sprite/get-size sprite)
             scaled-size (:scaled-frame-size settings)
             scaled-spritesheet-size {:width (* (:width scaled-size) (:columns settings))
                                      :height (* (:height scaled-size) (:rows settings))}
             image-canvas (->> (get-cels-for-rendering settings sprite)
                               (map (fn [cels]
                                      (->> (canvas/create-canvas size)
                                           (canvas/draw-cels-on-single-canvas cels)
                                           (canvas/scale size scaled-size))))
                               (canvas/combine scaled-size scaled-spritesheet-size (:columns settings)))]
         {:db db
          :fx [[::generate-plain-image {:canvas image-canvas
                                        :on-finish [::download-generated-blob (:file-name settings)]}]]})

       :image
       (let [settings (get-image-settings db)
             size (sprite/get-size sprite)
             scaled-size (:scaled-frame-size settings)
             rendered-frames
             (->> (get-cels-for-rendering settings sprite)
                  (map (fn [cels]
                         {:canvas (->> (canvas/create-canvas size)
                                       (canvas/draw-cels-on-single-canvas cels)
                                       (canvas/scale size scaled-size))
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
                               {:content (canvas/to-base64 canvas "png")
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

(re-frame/reg-event-fx
 ::download-generated-blob
 (fn [{:keys [db]} [_ file-name blob]]
   {:db (assoc-in db [:export :exporting] false)
    :fx [[:download-file {:file-name file-name :content blob}]]}))

(re-frame/reg-event-fx
 ::generate-gif-preview-success
 (fn [{:keys [db]} [_ gif]]
   {:db (assoc-in db [:export :preview] {:data [gif] :generation false})}))

(re-frame/reg-fx
 ::generate-zip
 (fn [{:keys [files-desc on-finish]}]
   (let [zip (jszip)]
     (doseq [{:keys [file-name content]} files-desc]
       (. zip (file file-name content #js {"base64" true})))
     (.. zip
         (generateAsync #js {"type" "blob"})
         (then (fn [blob]
                 (re-frame/dispatch (conj on-finish blob))))))))

(re-frame/reg-fx
 ::generate-plain-image ;; png, jpg and so on. todo: rename? 
 (fn [{:keys [canvas on-finish]}]
   (.. (canvas/->blob-promise canvas)
       (then (fn [images]
               (re-frame/dispatch (conj on-finish images)))))))

(defn- blob->base64 [blob]
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

;; 13) оптимизация для слинкованых ячеек
;; 5) избавиться от повторения упоминаний :image, :spritesheet(кнопки, сеттинги)
;; 15) переименовать import-export помдуль