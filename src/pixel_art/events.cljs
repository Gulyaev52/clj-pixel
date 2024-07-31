(ns pixel-art.events
  (:require ["tinycolor2" :as tinycolor]
            [pixel-art.canvas :as canvas]
            [pixel-art.db :as db :refer [get-layer-name initial-frame-duration
                                         max-scale]]
            [pixel-art.history :as history]
            [pixel-art.history.events :as history.events]
            [pixel-art.local-storage :as local-storage]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.palette :as palette]
            [pixel-art.tool.core :as tool]
            [pixel-art.sprite-import-export]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [pixel-art.tool.shape-select :as shape-select]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-current-cel]]
            [pixel-art.utils.geometry :as geometry]
            [pixel-art.utils.interceptor :refer [on-changes]]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [re-pressed.core :as rp]
            [sc.api]))

;; todo: remove
(add-watch re-frame.db/app-db :def
           (fn [_ _ _ new]
             (def db new)))

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx ::local-storage/get-item palette/local-storage-key)]
 (fn [coeffects [_ {:keys [initial-pixels-map palettes-info]}]]
   {:db (db/get-default-db {:palettes-info (or palettes-info
                                               (get coeffects palette/local-storage-key))
                            :initial-pixels-map initial-pixels-map})
    :fx [[:dispatch [::rp/set-keydown-rules
                     {:event-keys (concat history.events/hotkeys
                                          rectangle-select/hotkeys
                                          shape-select/hotkeys)}]]]}))

(re-frame/reg-event-fx
 ::initialize-canvas
 (fn []
   {:fx [[:init-canvases]
         [:draw-current-frame]
         [:draw-pixels-grid]
         [:zoom]]}))

(re-frame/reg-global-interceptor
 (on-changes
  :redraw-current-cel ;; это также нужно и на изменения слоя. например прозрачности или видимости
  #(-> % :sprite)
  (fn [{:keys [db]}]
    {:db db
     :fx [[:clear-preview]
          [:clear-frame]
          [:draw-current-frame]]})))

;; todo: fix performance; если позиция ячейки изменяется то тут ошибка; если удаляется
(re-frame/reg-global-interceptor
 (on-changes
  :generate-cel-imgs
  #(-> % :sprite :cels)
  (fn [{:keys [db]}]
    (let [cel-imgs (->> (-> db :sprite)
                        sprite/get-cels-with-pos-as-coll
                        (map (fn [cel] [(:pos cel)
                                        (canvas/generate-img #(canvas/draw-cel cel %)
                                                             (cel/get-size cel))]))
                        (into {}))]
      {:db (assoc db :cel-imgs cel-imgs)}))))

(re-frame/reg-event-fx
 ::select-tool
 (fn [{:keys [db]} [_ tool-type]]
   (let [tool (tool/init tool-type)]
     (commit-changes-and-init-tool db
                                   (get-in db [:tool :state :changes])
                                   tool))))

(re-frame/reg-event-fx
 ::change-tool-option
 (fn [{:keys [db]} [_ field value]]
   (let [tool-type (-> db :tool :type)]
     {:db (assoc-in db [:tools-options tool-type field] value)})))

(re-frame/reg-event-fx
 ::enable-pixels-grid
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc db :pixels-grid-enabled enabled)
    :fx [(if enabled
           [:draw-pixels-grid]
           [:hide-pixels-grid])]}))

(re-frame/reg-event-fx
 ::add-frame
 (fn [{:keys [db]}]
   (let [new-frame (frame/create initial-frame-duration)]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (update-in [:db :sprite] #(sprite/add-frame new-frame %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::remove-frame
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/remove-frame)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::duplicate-frame
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/duplicate-frame)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::select-frame
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-frame idx %)))))

(re-frame/reg-event-fx
 ::add-layer
 (fn [{:keys [db]}]
   (let [layer-name (get-layer-name :single (-> db :sprite :layers count))
         layer (layer/create layer-name nil)]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (update-in [:db :sprite] #(sprite/add-layer layer %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::duplicate-layer
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/duplicate-layer)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::remove-layer
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/remove-layer (-> db :sprite sprite/get-current-layer-idx)
                                                      %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::merge-layer-with-below
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/merge-layer-with-below)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer-up
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-layer-up (sprite/get-current-layer-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer-down
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-layer-down (sprite/get-current-layer-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::toggle-layer-visibility
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer idx #(update % :visibile? not) sprite))))))

(re-frame/reg-event-fx
 ::toggle-layer-automatic-linking
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer idx #(update % :automatic-linking? not) sprite))))))

(re-frame/reg-event-fx
 ::set-cel-opacity
 (fn [{:keys [db]} [_ opacity]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] #(sprite/set-current-cel-opacity opacity %)))))

(re-frame/reg-event-fx
 ::select-layer
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-layer idx %)))))

(re-frame/reg-event-fx
 ::select-only-1-cel
 (fn [{:keys [db]} [_ pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-only-1-cel pos %)))))

(re-frame/reg-event-fx
 ::toggle-cel-to-selection
 (fn [{:keys [db]} [_ pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/toggle-cel-to-selection pos %)))))

(re-frame/reg-event-fx
 ::add-cels-range-to-selection
 (fn [{:keys [db]} [_ pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/add-cels-range-to-selection pos %)))))

(re-frame/reg-event-fx
 ::link-selected-cels
 (fn [{:keys [db]} [_ main-cel-pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/link-selected-cels main-cel-pos %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::unlink-selected-cels
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/unlink-selected-cels)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::zoom
 (fn [{:keys [db]} [_ delta center-pos mouse-offset-pos]]
   (let [new-scale (-> db :scale (* delta))
         old-canvas-size (-> db :sprite sprite/get-size (update-vals #(* % (:scale db))))
         new-canvas-size (-> db :sprite sprite/get-size (update-vals #(* % new-scale)))
         canvas-size-diff (merge-with - new-canvas-size old-canvas-size)
         drawing-container-size (:drawing-container-size db)
         new-drawing-container-size (merge-with + drawing-container-size canvas-size-diff)
         old-canvas-pos {:x (- (/ (:width drawing-container-size) 2)
                               (/ (:width old-canvas-size) 2))
                         :y (- (/ (:height drawing-container-size) 2)
                               (/ (:height old-canvas-size) 2))}
         new-canvas-pos {:x (- (/ (:width new-drawing-container-size) 2)
                               (/ (:width new-canvas-size) 2))
                         :y (- (/ (:height new-drawing-container-size) 2)
                               (/ (:height new-canvas-size) 2))}
         new-viewport-scroll {:x (- (+ (* (- (:x mouse-offset-pos) (:x old-canvas-pos)) delta) (:x new-canvas-pos))
                                    (:x center-pos))
                              :y (- (+ (* (- (:y mouse-offset-pos) (:y old-canvas-pos)) delta) (:y new-canvas-pos))
                                    (:y center-pos))}]
     {:db (-> db
              (assoc :scale new-scale)
              (assoc :viewport-scroll new-viewport-scroll)
              (assoc :drawing-container-size new-drawing-container-size))
      :fx [[:zoom]]})))

(re-frame/reg-event-fx
 ::start-panning
 (fn [{:keys [db]} [_ pos]]
   {:db (assoc db
               :start-panning-pos pos
               :initial-viewport-scroll (:viewport-scroll db))}))

(re-frame/reg-event-fx
 ::pan
 (fn [{:keys [db]} [_ mouse-pos]]
   (let [delta-pos (merge-with - (:start-panning-pos db) mouse-pos)
         new-viewport-scroll (merge-with + (:initial-viewport-scroll db) delta-pos)]
     {:db (assoc db :viewport-scroll new-viewport-scroll)
      :fx [[:pan]]})))

(re-frame/reg-event-fx
 ::stop-panning
 (fn [{:keys [db]} [_]]
   {:db (assoc db
               :start-panning-pos nil
               :initial-viewport-scroll nil)}))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn [{:keys [db]} [_ event-type mouse-pos right-button]]
   (let [event {:type event-type :pos mouse-pos :right-button right-button}]
     (case event-type
       :mouse-down
       (-> (assoc db
                  :user-is-drawing true ;;todo: нужен ли если ли есть initial-mouse-down-pos 
                  :initial-mouse-down-pos (:pos event)
                  :mouse-pos (:pos event)) ;; todo: удалить? пока нужно только в select'ах
           (tool/handle-mouse-event event))

       :mouse-move
       (-> (assoc db
                  :user-is-drawing (:user-is-drawing db)
                  :mouse-pos (:pos event))
           (tool/handle-mouse-event event))

       :mouse-up
       (-> (assoc db
                  :user-is-drawing false
                  :mouse-pos (:pos event))
           (tool/handle-mouse-event event)
           (assoc-in [:db :initial-mouse-down-pos] nil))))))

(re-frame/reg-fx
 :init-canvases
 (fn []
   (let [preview-canvas (. js/document (getElementById "preview"))
         layers-below-canvas (. js/document (getElementById "layers-below"))
         layers-above-canvas (. js/document (getElementById "layers-above"))
         current-layer (. js/document (getElementById "current-layer"))
         onion-skin-canvas (. js/document (getElementById "onion-skin"))

         drawing-container-size (:drawing-container-size @re-frame.db/app-db)
         sprite-size (-> @re-frame.db/app-db :sprite sprite/get-size)
         scale (-> @re-frame.db/app-db :scale)
         drawing-canvas-container (.. js/document (getElementById "drawing-canvas-container"))
         canvas-layers (.. js/document (getElementById "canvas-layers"))]
     (doseq [canvas [preview-canvas onion-skin-canvas layers-below-canvas layers-above-canvas current-layer]]
       (set! (. canvas -width) (:width sprite-size))
       (set! (. canvas -height) (:height sprite-size)))
     (set! (.. canvas-layers -style -width) (str (* (:width sprite-size) scale) "px"))
     (set! (.. canvas-layers -style -height) (str (* (:height sprite-size) scale) "px"))

     (let [grid-canvas (.. js/document (getElementById "grid"))]
       (set! (.. grid-canvas -width) (* (:width sprite-size) scale))
       (set! (.. grid-canvas -height) (* (:height sprite-size) scale)))

     (set! (.. drawing-canvas-container -style -width) (str (:width drawing-container-size) "px"))
     (set! (.. drawing-canvas-container -style -height) (str (:height drawing-container-size) "px")))))

(defn- update-viewport-scroll []
  (let [viewport (.. js/document (getElementById "viewport"))
        viewport-scroll (:viewport-scroll @re-frame.db/app-db)]
    (set! (.. viewport -scrollTop) (:y viewport-scroll))
    (set! (.. viewport -scrollLeft) (:x viewport-scroll))))

(defn- draw-pixels-grid []
  (let [db @re-frame.db/app-db

        canvas (. js/document (getElementById "grid"))
        ctx (. canvas (getContext "2d"))

        sprite-size (-> db :sprite sprite/get-size)
        scale (:scale db)
        canvas-size {:width (* scale (:width sprite-size))
                     :height (* scale (:height sprite-size))}]
    (.. ctx save)
    (set! (. ctx -strokeStyle) (str "rgba(0, 0, 255, " (min (/ scale max-scale) 1) "")) ;;todo: не видно на голубом
    (dotimes [y (:height sprite-size)]
      (doto ctx
        (.beginPath)
        (.moveTo 0 (* y scale))
        (.lineTo (:width canvas-size) (* y scale))
        (.stroke)))
    (dotimes [x (:width sprite-size)]
      (doto ctx
        (.beginPath)
        (.moveTo (* x scale) 0)
        (.lineTo (* x scale) (:height canvas-size))
        (.stroke)))
    (.. ctx restore)))

(re-frame/reg-fx
 :zoom
 (fn []
   (let [canvas-layers (.. js/document (getElementById "canvas-layers"))
         sprite-size (-> @re-frame.db/app-db :sprite sprite/get-size)
         scale (-> @re-frame.db/app-db :scale)
         new-sprite-size {:width (* (:width sprite-size) scale)
                          :height (* (:height sprite-size) scale)}]
     (set! (.. canvas-layers -style -width) (str (:width new-sprite-size) "px"))
     (set! (.. canvas-layers -style -height) (str (:height new-sprite-size) "px"))

     (when (:pixels-grid-enabled @re-frame.db/app-db)
       (let [grid-canvas (.. js/document (getElementById "grid"))]
         (set! (.. grid-canvas -width) (:width new-sprite-size))
         (set! (.. grid-canvas -height) (:height new-sprite-size))
         (draw-pixels-grid)))

     (let [drawing-canvas-container (.. js/document (getElementById "drawing-canvas-container"))
           drawing-container-size (:drawing-container-size @re-frame.db/app-db)]
       (set! (.. drawing-canvas-container -style -width) (str (:width drawing-container-size) "px"))
       (set! (.. drawing-canvas-container -style -height) (str (:height drawing-container-size) "px")))

     (update-viewport-scroll))))

(re-frame/reg-fx
 :pan
 (fn []
   (update-viewport-scroll)))

(defn get-highlight-color [color]
  (let [dark-color "rgba(0, 0, 0, 0.2)"
        light-color "rgba(255, 255, 255, 0.2)"]
    (if (= color transparent-color)
      dark-color
      (let [luminance (.. (tinycolor color) toHsl -l)]
        (if (> luminance 0.5)
          dark-color
          light-color)))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (let [ctx (canvas/get-canvas-context "preview")
         size (sprite/get-size (:sprite @re-frame.db/app-db))
         current-cel (get-current-cel @re-frame.db/app-db)]
     (doseq [pos poses]
       (when (geometry/valid-point? pos size)
         (set! (. ctx -fillStyle) (->> (cel/get-pixel pos current-cel)
                                       get-highlight-color))
         (. ctx (fillRect (:x pos) (:y pos) 1 1)))))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "preview")))))

(re-frame/reg-fx
 :draw-preview
 (fn [changes]
   (let [ctx (canvas/get-canvas-context "preview")
         size (-> @re-frame.db/app-db :sprite sprite/get-size)]
     (doseq [[pos color] changes]
       (when (geometry/valid-point? pos size)
         (set! (. ctx -fillStyle) (or color "white"))
         (. ctx (fillRect (:x pos) (:y pos) 1 1)))))))

(re-frame/reg-fx
 :hide-pixels-grid
 (fn [_]
   (canvas/clear-canvas (. js/document (getElementById "grid")))))

(re-frame/reg-fx
 :draw-pixels-grid
 draw-pixels-grid)

;; todo: rename
(re-frame/reg-fx
 :clear-frame
 (fn []
   (canvas/clear-canvases (vec (. js/document (getElementsByClassName "layer"))))))

;; todo: rename to current-frame
(re-frame/reg-fx
 :draw-current-frame
 (fn []
   (let [{:keys [sprite]} @re-frame.db/app-db]
     (canvas/draw-frame (sprite/get-current-frame-idx sprite) sprite))))

(re-frame/reg-fx
 :show-alert
 (fn [message]
   (js/alert message)))

(re-frame/reg-fx
 :show-confirm
 (fn [message on-confirm]
   (when (js/confirm message)
     (re-frame/dispatch on-confirm))))

(re-frame/reg-fx
 :download-file ;; todo: move to another place
 (fn [{:keys [file-name content]}]
   (let [data-blob (js/Blob. #js [content] #js {:type "application/json"})
         link (.createElement js/document "a")]
     (set! (.-href link) (.createObjectURL js/URL data-blob))
     (.setAttribute link "download" file-name)
     (.appendChild (.-body js/document) link)
     (.click link)
     (.removeChild (.-body js/document) link))))