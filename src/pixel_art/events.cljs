(ns pixel-art.events
  (:require ["tinycolor2" :as tinycolor]
            [day8.re-frame.tracing :refer [fn-traced]]
            [pixel-art.canvas :as canvas]
            [pixel-art.db :as db :refer [max-scale]]
            [pixel-art.history :as history]
            [pixel-art.history.events :as history.events]
            [pixel-art.local-storage :as local-storage]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.palette :as palette]
            [pixel-art.tool.core :as tool]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [pixel-art.tool.shape-select :as shape-select]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-current-frame]]
            [pixel-art.utils.geometry :as geometry]
            [pixel-art.utils.interceptor :refer [on-changes]]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [re-pressed.core :as rp]
            [sc.api]))

(defn draw-pixels-grid []
  (let [db @re-frame.db/app-db

        canvas (. js/document (getElementById "grid"))
        ctx (. canvas (getContext "2d"))

        frame-size (-> db get-current-frame frame/get-size)
        scale (:scale db)
        canvas-size {:width (* scale (:width frame-size))
                     :height (* scale (:height frame-size))}]
    (.. ctx save)
    (set! (. ctx -strokeStyle) (str "rgba(0, 0, 255, " (min (/ scale max-scale) 1) "")) ;;todo: не видно на голубом
    (dotimes [y (:height frame-size)]
      (doto ctx
        (.beginPath)
        (.moveTo 0 (* y scale))
        (.lineTo (:width canvas-size) (* y scale))
        (.stroke)))
    (dotimes [x (:width frame-size)]
      (doto ctx
        (.beginPath)
        (.moveTo (* x scale) 0)
        (.lineTo (* x scale) (:height canvas-size))
        (.stroke)))
    (.. ctx restore)))

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx ::local-storage/get-item palette/local-storage-key)]
 (fn [coeffects _]
   {:db (db/get-default-db (get coeffects palette/local-storage-key))
    :fx [[:dispatch [::rp/set-keydown-rules
                     {:event-keys (concat history.events/hotkeys
                                          rectangle-select/hotkeys
                                          shape-select/hotkeys)}]]]}))

(re-frame/reg-event-fx
 ::initialize-canvas
 (fn [{:keys [db]} _]
   {:fx [[:init-canvases]
         [:draw-frame (get-current-frame db)] ;; клэш с run-fx-on-changes
         [:draw-pixels-grid]
         [:zoom]]}))

(re-frame/reg-global-interceptor
 (on-changes
  :redraw-current-frame
  get-current-frame
  (fn [{:keys [db new]}]
    {:db db
     :fx [[:clear-preview]
          [:clear-frame]
          [:draw-frame new]]})))

(defn- generate-frame-img [frame]
  (let [canvas-size (:size frame)
        canvas-elem (.. js/document (createElement "canvas"))]
    (set! (. canvas-elem -width) (:width canvas-size))
    (set! (. canvas-elem -height) (:height canvas-size))
    (canvas/draw-frame frame 1 canvas-elem)
    (. canvas-elem (toDataURL "image/png"))))

(re-frame/reg-global-interceptor
 (on-changes
  :generate-frame-imgs ;; todo: явно менять или же если будут id у фреймов то будет ок?
  #(-> % :sprite)
  (fn [{:keys [db old new]}]
    (let [new-frame-imgs (->> (range 0 (count (:frames (or old new))))
                              (filter (fn [idx]
                                        (not (identical? (nth (:frames new) idx nil)
                                                         (nth (:frames old) idx nil)))))
                              (map (fn [idx]
                                     [idx (when-let [frame (nth (:frames new) idx nil)]
                                            (generate-frame-img frame))]))
                              (into {}))]
      {:db (update db :frame-imgs #(->> (merge % new-frame-imgs)
                                        (remove (fn [[_ v]] (nil? v)))
                                        (into {})))}))))

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
 (fn [{:keys [db]} _]
   (let [sprite (:sprite db)
         new-frame (frame/create (:size sprite))]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (update-in [:db :sprite] #(sprite/add-frame new-frame %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::remove-frame
 (fn [{:keys [db]} [_ idx]]
   (let [sprite (:sprite db)
         new-sprite (sprite/remove-frame idx sprite)]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (assoc-in [:db :sprite] new-sprite)
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::duplicate-frame
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/duplicate-frame idx %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::select-frame
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-frame idx %)))))

(re-frame/reg-event-fx
 ::zoom
 (fn [{:keys [db]} [_ delta center-pos mouse-offset-pos]]
   (let [new-scale (-> db :scale (* delta))
         old-canvas-size (-> db (get-current-frame) frame/get-size (update-vals #(* % (:scale db))))
         new-canvas-size (-> db (get-current-frame) frame/get-size (update-vals #(* % new-scale)))
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
              (update :scale #(* % delta))
              (assoc :viewport-scroll new-viewport-scroll)
              (assoc :drawing-container-size new-drawing-container-size))
      :fx [[:zoom]]})))

(re-frame/reg-event-fx
 ::start-panning
 (fn [{:keys [db]} [_ pos]]
   {:db (assoc db
               :start-viewport-scroll pos
               :initial-viewport-scroll (:viewport-scroll db))}))

(re-frame/reg-event-fx
 ::pan
 (fn [{:keys [db]} [_ mouse-pos]]
   (let [delta-pos (merge-with - (:start-viewport-scroll db) mouse-pos)
         new-viewport-scroll (merge-with + (:initial-viewport-scroll db) delta-pos)]
     {:db (assoc db :viewport-scroll new-viewport-scroll)
      :fx [[:zoom]]})))

(re-frame/reg-event-fx
 ::stop-panning
 (fn [{:keys [db]} [_]]
   {:db (assoc db
               :start-viewport-scroll nil
               :initial-viewport-scroll nil)}))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn-traced [{:keys [db]} [_ event-type mouse-pos right-button]]
            (let [event {:type event-type :pos mouse-pos :right-button right-button}]
              (case event-type
                :mouse-down
                (-> (assoc db
                           :user-is-drawing true ;;todo: нужен ли если ли есть initial-mouse-down-pos 
                           :initial-mouse-down-pos (:pos event)
                           :last-mouse-pos (:pos event)) ;; todo: удалить? пока нужно только в select'ах
                    (tool/handle-mouse-event event))

                :mouse-move
                (-> (assoc db
                           :user-is-drawing (:user-is-drawing db)
                           :last-mouse-pos (:pos event))
                    (tool/handle-mouse-event event))

                :mouse-up
                (-> (assoc db
                           :user-is-drawing false
                           :last-mouse-pos (:pos event))
                    (tool/handle-mouse-event event)
                    (assoc-in [:db :initial-mouse-down-pos] nil))))))

(defn get-highlight-color [color]
  (let [dark-color "rgba(0, 0, 0, 0.2)"
        light-color "rgba(255, 255, 255, 0.2)"]
    (if (= color frame/transparent-color)
      dark-color
      (let [luminance (.. (tinycolor color) toHsl -l)]
        (if (> luminance 0.5)
          dark-color
          light-color)))))

(re-frame/reg-fx
 :init-canvases
 (fn []
   (let [preview-canvas (. js/document (getElementById "preview"))
         main-canvas (. js/document (getElementById "tutorial"))
         onion-skin-canvas (. js/document (getElementById "onion-skin"))

         drawing-container-size (:drawing-container-size @re-frame.db/app-db)
         frame-size (-> @re-frame.db/app-db get-current-frame frame/get-size)
         scale (-> @re-frame.db/app-db :scale)
         drawing-canvas-container (.. js/document (getElementById "drawing-canvas-container"))
         canvas-layers (.. js/document (getElementById "canvas-layers"))]
     (doseq [canvas [preview-canvas main-canvas onion-skin-canvas]]
       (set! (. canvas -width) (:width frame-size))
       (set! (. canvas -height) (:height frame-size)))
     (set! (.. canvas-layers -style -width) (str (* (:width frame-size) scale) "px"))
     (set! (.. canvas-layers -style -height) (str (* (:height frame-size) scale) "px"))

     (let [grid-canvas (.. js/document (getElementById "grid"))]
       (set! (.. grid-canvas -width) (* (:width frame-size) scale))
       (set! (.. grid-canvas -height) (* (:height frame-size) scale)))

     (set! (.. drawing-canvas-container -style -width) (str (:width drawing-container-size) "px"))
     (set! (.. drawing-canvas-container -style -height) (str (:height drawing-container-size) "px")))))

(re-frame/reg-fx
 :zoom
 (fn []
   (let [canvas-layers (.. js/document (getElementById "canvas-layers"))
         frame-size (-> @re-frame.db/app-db get-current-frame frame/get-size)
         scale (-> @re-frame.db/app-db :scale)
         new-frame-size {:width (* (:width frame-size) scale)
                         :height (* (:height frame-size) scale)}]
     (set! (.. canvas-layers -style -width) (str (:width new-frame-size) "px"))
     (set! (.. canvas-layers -style -height) (str (:height new-frame-size) "px"))

     (when (:pixels-grid-enabled @re-frame.db/app-db)
       (let [grid-canvas (.. js/document (getElementById "grid"))]
         (set! (.. grid-canvas -width) (:width new-frame-size))
         (set! (.. grid-canvas -height) (:height new-frame-size))
         (draw-pixels-grid)))

     (let [drawing-canvas-container (.. js/document (getElementById "drawing-canvas-container"))
           drawing-container-size (:drawing-container-size @re-frame.db/app-db)]
       (set! (.. drawing-canvas-container -style -width) (str (:width drawing-container-size) "px"))
       (set! (.. drawing-canvas-container -style -height) (str (:height drawing-container-size) "px")))

     (let [viewport (.. js/document (getElementById "viewport"))
           viewport-scroll (:viewport-scroll @re-frame.db/app-db)]
       (set! (.. viewport -scrollTop) (:y viewport-scroll))
       (set! (.. viewport -scrollLeft) (:x viewport-scroll))))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (let [ctx (canvas/get-canvas-context "preview")
         current-frame (get-current-frame @re-frame.db/app-db)]
     (doseq [pos poses]
       (when (geometry/valid-point? pos (:size current-frame))
         (set! (. ctx -fillStyle) (->> (frame/get-pixel pos current-frame)
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
         current-frame (get-current-frame @re-frame.db/app-db)]
     (doseq [[pos color] changes]
       (when (geometry/valid-point? pos (:size current-frame))
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
   (canvas/clear-canvas (. js/document (getElementById "tutorial")))))

;; todo: rename to current-frame
(re-frame/reg-fx
 :draw-frame
 (fn [frame]
   (canvas/draw-frame frame 1 (. js/document (getElementById "tutorial")))))
