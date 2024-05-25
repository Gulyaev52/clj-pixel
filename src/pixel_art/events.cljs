(ns pixel-art.events
  (:require ["tinycolor2" :as tinycolor]
            [day8.re-frame.tracing :refer [fn-traced]]
            [pixel-art.canvas :as canvas]
            [pixel-art.db :as db]
            [pixel-art.history :as history]
            [pixel-art.history.events :as history.events]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.sprite :as sprite]
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
            [pixel-art.local-storage :as local-storage]
            [sc.api]
            [pixel-art.palette :as palette]))

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
         [:draw-pixels-grid]]}))

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
 (fn [{:keys [db]} [_ delta center-pos]]
   (let [prev-canvas-offset (:canvas-offset db)
         new-canvas-offset {:x (- (:x center-pos)
                                  (* (- (:x center-pos) (:x prev-canvas-offset)) delta))
                            :y (- (:y center-pos)
                                  (* (- (:y center-pos) (:y prev-canvas-offset)) delta))}]
     {:db (-> db
              (update :scale #(* % delta))
              (assoc :canvas-offset new-canvas-offset))
      :fx [[:clear-frame]
           [:draw-frame (get-current-frame db)]
           [:hide-pixels-grid]
           [:draw-pixels-grid]
           [:hide-onion-skin]
           [:draw-onion-skin {:sprite (:sprite db) :opacity (-> db :onion-skin :opacity)}]]})))

(re-frame/reg-event-fx
 ::start-panning
 (fn [{:keys [db]} [_ pos]]
   {:db (assoc db
               :start-canvas-offset pos
               :initial-canvas-offset (:canvas-offset db))}))

(re-frame/reg-event-fx
 ::pan
 (fn [{:keys [db]} [_ mouse-pos]]
   (let [delta-pos (merge-with - mouse-pos (:start-canvas-offset db))
         new-canvas-offset (merge-with + (:initial-canvas-offset db) delta-pos)]
     {:db (assoc db :canvas-offset new-canvas-offset)
      :fx [[:clear-frame]
           [:draw-frame (get-current-frame db)]
           [:hide-pixels-grid]
           [:draw-pixels-grid]
           [:hide-onion-skin]
           [:draw-onion-skin {:sprite (:sprite db) :opacity (-> db :onion-skin :opacity)}]]})))

(re-frame/reg-event-fx
 ::stop-panning
 (fn [{:keys [db]} [_]]
   {:db (assoc db
               :start-canvas-offset nil
               :initial-canvas-offset nil)}))

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
         grid-canvas (. js/document (getElementById "grid"))
         onion-skin-canvas (. js/document (getElementById "onion-skin"))

         canvas-size (:canvas-size @re-frame.db/app-db)]
     (set! (. preview-canvas -width) (:width canvas-size))
     (set! (. preview-canvas -height) (:height canvas-size))
     (set! (. main-canvas -width) (:width canvas-size))
     (set! (. main-canvas -height) (:height canvas-size))
     (set! (. grid-canvas -width) (:width canvas-size))
     (set! (. grid-canvas -height) (:height canvas-size))
     (set! (. onion-skin-canvas -width) (:width canvas-size))
     (set! (. onion-skin-canvas -height) (:height canvas-size)))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (canvas/draw-on-zoomed-canvas
    (. js/document (getElementById "preview"))
    @re-frame.db/app-db
    (fn [{:keys [ctx]}]
      (let [current-frame (get-current-frame @re-frame.db/app-db)]
        (doseq [pos poses]
          (when (geometry/valid-point? pos (:size current-frame))
            (set! (. ctx -fillStyle) (->> (frame/get-pixel pos current-frame)
                                          get-highlight-color))
            (. ctx (fillRect (:x pos) (:y pos) 1 1)))))))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "preview")))))

(re-frame/reg-fx
 :draw-preview
 (fn [changes]
   (canvas/draw-on-zoomed-canvas
    (. js/document (getElementById "preview"))
    @re-frame.db/app-db
    (fn [{:keys [ctx]}]
      (let [current-frame (get-current-frame @re-frame.db/app-db)]
        (doseq [[pos color] changes]
          (when (geometry/valid-point? pos (:size current-frame))
            (set! (. ctx -fillStyle) (or color "white"))
            (. ctx (fillRect (:x pos) (:y pos) 1 1)))))))))

(re-frame/reg-fx
 :hide-pixels-grid
 (fn [_]
   (canvas/clear-canvas (. js/document (getElementById "grid")))))

(re-frame/reg-fx
 :draw-pixels-grid
 (fn [_]
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "grid"))
         ctx (. canvas (getContext "2d"))

         canvas-offset (:canvas-offset db)
         frame-size (-> db get-current-frame frame/get-size)
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]
     (.. ctx save)
     (set! (. ctx -strokeStyle) "blue") ;;todo: не видно на голубом
     (.. ctx (translate (:x canvas-offset) (:y canvas-offset)))
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
     (.. ctx restore))))

;; todo: rename
(re-frame/reg-fx
 :clear-frame
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "tutorial")))))

;; todo: rename to current-frame
(re-frame/reg-fx
 :draw-frame
 (fn [frame]
   (canvas/draw-on-zoomed-canvas
    (. js/document (getElementById "tutorial"))
    @re-frame.db/app-db
    (fn [{:keys [canvas]}] (canvas/draw-frame frame 1 canvas)))))

(comment
  (defn scale-canvas [canvas scale]
    (let [imageData (.. canvas
                        (getContext "2d")
                        (getImageData 0 0 (.. canvas -width) (.. canvas -height)))
          new-canvas (.. js/document (createElement "canvas"))]
      (set! (.. new-canvas -width) (.. imageData -width))
      (set! (.. new-canvas -height) (.. imageData -height))
      (.. new-canvas (getContext "2d") (putImageData imageData 0 0))

      (.. canvas (getContext "2d") save)
      (canvas/clear-canvas canvas)
      (.. canvas (getContext "2d") (drawImage new-canvas
                                              0
                                              0
                                              (* (.. canvas -width) scale)
                                              (* (.. canvas -height) scale)))
      (.. canvas (getContext "2d") restore))))
