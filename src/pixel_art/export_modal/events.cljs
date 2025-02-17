(ns pixel-art.export-modal.events
  (:require
   ["jszip" :as jszip]
   [clojure.string :as string]
   [pixel-art.canvas :as canvas]
   [pixel-art.model.sprite :as sprite]
   [re-frame.core :as re-frame]))

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

(defn- get-cels-for-rendering [settings sprite]
  (let [selected-poses (sprite/get-selected-cels-pos sprite)]
    (as-> (sprite/get-denormalized-cels sprite) $
      (if (= (:frames settings) :selected)
        (->> (sort-by :frame-idx (map :frame-idx selected-poses))
             (map #(nth $ %)))
        $)

      (case (-> settings :layers :type)
        :visible
        (map (fn [frame-cels] (filter #(-> % :layer :visible?) frame-cels)) $)

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

(defn- adjust-columns-if-need [columns db]
  (let [cels-for-rendering (get-cels-for-rendering (-> db :export-modal :common-settings) (-> db :sprite))]
    (min (max 1 columns) (count cels-for-rendering))))

(defn get-common-settings-res [db]
  (let [common-settings (-> db :export-modal :common-settings)
        sprite-size (-> db :sprite sprite/get-size)
        scaled-frame-size (update-vals sprite-size #(. js/Math (round (* (:scale common-settings) %))))]
    (assoc common-settings :scaled-frame-size scaled-frame-size)))

(defn get-spritesheet-settings [db]
  (let [common-settings (get-common-settings-res db)
        spritesheet-settings (-> db :export-modal :spritesheet-settings)
        rows (->> (get-cels-for-rendering common-settings (-> db :sprite)) ;; todo: pass it as arg?
                  (partition-all (:columns spritesheet-settings))
                  count)]
    (-> spritesheet-settings
        (assoc :rows rows)
        (merge common-settings))))

(defn get-image-settings [db]
  (let [common-settings (get-common-settings-res db)]
    (merge common-settings (-> db :export-modal :image-settings))))

(defn generate-preview [db]
  (let [db (assoc-in db [:export-modal :preview :generation] true)
        {:keys [sprite export-modal]} db]
    (case (:current-tab export-modal)
      :spritesheet
      (let [settings (get-spritesheet-settings db)
            size (sprite/get-size sprite)
            spritesheet-size {:width (* (:width size) (:columns settings))
                              :height (* (:height size) (:rows settings))}
            img (->> (get-cels-for-rendering settings sprite)
                     (map #(canvas/draw-cels-on-single-canvas % (canvas/create-canvas size)))
                     (canvas/combine size spritesheet-size (:columns settings))
                     (#(canvas/to-data-url % "png")))]
        {:db (assoc-in db [:export-modal :preview] {:data img :generation false})})

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
            {:db (assoc-in db [:export-modal :preview] {:data data :generation false})})

          :gif
          {:db db
           :fx [[::generate-gif {:rendered-frames rendered-frames
                                 :repeat (:repeat settings)
                                 :base64 true
                                 :size size
                                 :on-finish [::generate-gif-preview-success]}]]})))))

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   (if opened
     (-> db
         (assoc-in [:export-modal :opened] opened)
         (update-in [:export-modal :spritesheet-settings :columns]
                    #(adjust-columns-if-need % db))
         generate-preview)
     {:db (assoc-in db [:export-modal :opened] opened)})))

(re-frame/reg-event-fx
 ::select-tab
 (fn [{:keys [db]} [_ tab]]
   (-> (cond-> db
         true (assoc-in [:export-modal :current-tab] tab)
         (= tab :spritesheet) (update-in [:export-modal :common-settings :file-type]
                                         #(if (= % :gif) :png %)))
       generate-preview)))

(re-frame/reg-event-fx
 ::set-settings-option
 (fn [{:keys [db]} [_ option-key value]]
   (-> (case option-key
         :columns (-> db
                      (assoc-in [:export-modal :spritesheet-settings option-key] value))
         :repeat (-> db
                     (assoc-in [:export-modal :image-settings option-key] value))
         (-> db
             (assoc-in [:export-modal :common-settings option-key] value)))
       (#(if (some #{option-key} [:columns :frames :layers :split-layers])
           (update-in % [:export-modal :spritesheet-settings :columns]
                      (fn [value] (adjust-columns-if-need value %)))
           %))
       (#(if-not (some #{option-key} [:file-name :scale])
           (generate-preview %)
           {:db %})))))

(re-frame/reg-event-fx
 ::export
 (fn [{:keys [db]}]
   (let [db (assoc-in db [:export-modal :exporting] true)
         {:keys [sprite export-modal]} db]
     (case (:current-tab export-modal)
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
                                  :size size
                                  :on-finish [::download-generated-blob (:file-name settings)] ;; todo: fix
                                  }]]}))))))

(re-frame/reg-event-fx
 ::download-generated-blob
 (fn [{:keys [db]} [_ file-name blob]]
   {:db (assoc-in db [:export-modal :exporting] false)
    :fx [[:download-file {:file-name file-name :content blob}]]}))

(re-frame/reg-event-fx
 ::generate-gif-preview-success
 (fn [{:keys [db]} [_ gif]]
   {:db (assoc-in db [:export-modal :preview] {:data [gif] :generation false})}))

(re-frame/reg-fx
 ::generate-zip
 (fn [{:keys [files-desc on-finish]}]
   (let [zip (jszip.)]
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
 (fn [{:keys [rendered-frames base64 repeat size on-finish]}]
   (let [GIF (. js/window -GIF)
         gif (GIF. (clj->js {"workers" 2
                             "quality" 1
                             "width" (:width size)
                             "height" (:height size)
                             "repeat" (if repeat 0 -1)}))

         frame-with-background-canvas (canvas/create-canvas size)
         frame-with-background-canvas-ctx (. frame-with-background-canvas (getContext "2d"))]
     (set! (. frame-with-background-canvas-ctx -fillStyle) "#ffffff") ;; todo: calc transparent color

     (doseq [{:keys [canvas cels]} rendered-frames]
       (let [cel (first cels)] ;; todo: refactor?
         (. frame-with-background-canvas-ctx (clearRect 0 0 (:width size) (:height size)))
         (. frame-with-background-canvas-ctx (fillRect 0 0 (:width size) (:height size)))
         (. frame-with-background-canvas-ctx (drawImage canvas 0 0 (:width size) (:height size)))
         (. gif (addFrame frame-with-background-canvas-ctx #js {"delay" (-> cel :frame :duration)
                                                                "copy" true}))))
     (. gif (on "finished" (fn [blob]
                             (if base64
                               (.. (blob->base64 blob)
                                   (then (fn [blob-as-base64]
                                           (re-frame/dispatch (conj on-finish blob-as-base64)))))
                               (re-frame/dispatch (conj on-finish blob))))))
     (. gif (render)))))