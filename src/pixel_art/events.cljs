(ns pixel-art.events
  (:require ["tinycolor2" :as tinycolor]
            [day8.re-frame.tracing :refer [fn-traced]]
            [pixel-art.db :as db]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.core :as tool]
            [pixel-art.tool.utils :refer [get-current-frame]]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [pixel-art.model.sprite :as sprite]))

(re-frame/reg-event-fx
 ::initialize-db
 (fn [_ _]
   {:db db/default-db}))

(re-frame/reg-event-fx
 ::initialize-canvas
 (fn [{:keys [db]} _]
   {:fx [[:init-canvases]
         [:draw-frame (get-current-frame db)]
         [:draw-pixels-grid]]}))

(re-frame/reg-event-db
 ::select-tool
 (fn [db [_ tool-type]]
   (let [tool (tool/init tool-type)]
     (assoc db :tool tool))))

(re-frame/reg-event-db
 ::change-tool-option
 (fn [db [_ field value]]
   (let [tool-type (-> db :tool :type)]
     (assoc-in db [:tools-options tool-type field] value))))

(re-frame/reg-event-fx
 ::enable-pixels-grid
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc db :pixels-grid-enabled enabled)
    :fx [(if enabled
           [:draw-pixels-grid]
           [:hide-pixels-grid])]}))

(re-frame/reg-event-db
 ::add-frame
 (fn [db _]
   (let [sprite (:sprite db)
         new-frame (frame/create (:size sprite))]
     (update db :sprite #(sprite/add-frame new-frame %)))))

(re-frame/reg-event-db
 ::remove-frame
 (fn [db [_ idx]]
   (update db :sprite #(sprite/remove-frame idx %))))

(re-frame/reg-event-db
 ::duplicate-frame
 (fn [db [_ idx]]
   (update db :sprite #(sprite/duplicate-frame idx %))))

(re-frame/reg-event-db
 ::select-frame
 (fn [db [_ idx]]
   (update db :sprite #(sprite/select-frame idx %))))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn-traced [{:keys [db]} [_ event-type mouse-pos]]
            (let [event {:type event-type :pos mouse-pos}]
              (case event-type
                :mouse-down
                (->> (assoc db
                            :user-is-drawing true ;;todo: нужен ли если ли есть initial-mouse-down-pos 
                            :initial-mouse-down-pos (:pos event)
                            :last-mouse-pos (:pos event)) ;; todo: удалить? пока нужно только в select'ах
                     (tool/handle-mouse-event event))

                :mouse-move
                (->> (assoc db
                            :user-is-drawing (:user-is-drawing db)
                            :last-mouse-pos (:pos event))
                     (tool/handle-mouse-event event))

                :mouse-up
                (-> (assoc db
                           :user-is-drawing false
                           :last-mouse-pos (:pos event))
                    (#(tool/handle-mouse-event event %)) ;; todo: оменять порядок арг
                    (assoc-in [:db :initial-mouse-down-pos] nil))))))

#_(api/last-ep-id)
#_(defsc [709 -83])
#_(pixel-art.events.event-collector/repeat-last-event)
#_(re-frame/dispatch (second @pixel-art.events.event-collector/event-store))

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
   (let [db @re-frame.db/app-db

         preview-canvas (. js/document (getElementById "preview"))
         main-canvas (. js/document (getElementById "tutorial"))
         grid-canvas (. js/document (getElementById "grid"))

         frame-size (-> db get-current-frame frame/get-size)
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]

     (set! (. preview-canvas -width) (:width canvas-size))
     (set! (. preview-canvas -height) (:height canvas-size))
     (set! (. main-canvas -width) (:width canvas-size))
     (set! (. main-canvas -height) (:height canvas-size))
     (set! (. grid-canvas -width) (:width canvas-size))
     (set! (. grid-canvas -height) (:height canvas-size)))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (let [canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))]
     (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height))))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))

         current-frame (get-current-frame db)
         scale (:scale db)]
     (doseq [pos poses]
       (set! (. ctx -fillStyle) (->> (frame/get-pixel pos current-frame)
                                     get-highlight-color))
       (. ctx (fillRect (* (:x pos) scale) (* (:y pos) scale) scale scale))))))

(re-frame/reg-fx
 :draw-preview
 (fn [preview]
   (let [db @re-frame.db/app-db
         canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))
         scale (:scale db)]

     (doseq [[pos color] preview]
       (set! (. ctx -fillStyle) (or color "white"))
       (. ctx (fillRect (* (:x pos) scale) (* (:y pos) scale) scale scale))))))

(re-frame/reg-fx
 :hide-pixels-grid
 (fn [_]
   (let [canvas (. js/document (getElementById "grid"))
         ctx (. canvas (getContext "2d"))]
     (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height))))))

(re-frame/reg-fx
 :draw-pixels-grid
 (fn [_]
   (println "bla")
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "grid"))
         ctx (. canvas (getContext "2d"))

         frame-size (-> db get-current-frame frame/get-size)
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]
     (set! (. ctx -strokeStyle) "blue") ;;todo: не видно на голубом
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
         (.stroke))))))

(re-frame/reg-fx
 :draw-frame
 (fn [frame]
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "tutorial"))
         ctx (. canvas (getContext "2d"))

         frame-size (frame/get-size frame)
         scale (:scale db)]
     (doseq [x (range 0 (:width frame-size))
             y (range 0 (:height frame-size))]
       (set! (. ctx -fillStyle) (or (frame/get-pixel {:x x :y y} frame)
                                    "white"))
       (. ctx (fillRect (* x scale) (* y scale) scale scale))))))
