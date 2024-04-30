(ns pixel-art.events
  (:require ["tinycolor2" :as tinycolor]
            [day8.re-frame.tracing :refer [fn-traced]]
            [pixel-art.db :as db]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.pen :as pen]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [re-frame.core :as re-frame]
            [re-frame.db]))

(re-frame/reg-event-fx
 ::initialize-db
 (fn [_ _]
   {:db db/default-db}))

(re-frame/reg-event-fx
 ::initialize-canvas
 (fn [{:keys [db]} _]
   {:draw-frame (:source-frame db)
    :draw-preview []}))

(re-frame/reg-event-db
 ::select-tool
 (fn [db [_ tool-type]]
   (let [tool (case tool-type
                :pen (pen/init)
                :rectangle (rectangle/init)
                :rectangle-select (rectangle-select/init))]
     (assoc db :tool tool))))

(re-frame/reg-event-db
 ::change-tool-option
 (fn [db [_ field value]]
   (let [tool-type (-> db :tool :type)]
     (assoc-in db [:tools-options tool-type field] value))))

(defn- handle-mouse-event-by-tool [event db]
  (case (-> db :tool :type)
    :pen (pen/handle-mouse-event event db)
    :rectangle (rectangle/handle-mouse-event event db)
    :rectangle-select (rectangle-select/handle-mouse-event event db)))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn-traced [{:keys [db]} [_ event-type mouse-pos]]
            (let [event {:type event-type :pos mouse-pos}]
              (case event-type
                :mouse-down
                (->> (assoc db
                            :user-is-drawing true ;;todo: нужен ли если ли есть initial-mouse-down-pos 
                            :initial-mouse-down-pos (:pos event))
                     (handle-mouse-event-by-tool event))
                :mouse-move
                (->> (assoc db :user-is-drawing (:user-is-drawing db))
                     (handle-mouse-event-by-tool event))
                :mouse-up
                (->> (assoc db
                            :user-is-drawing false
                            :initial-mouse-down-pos nil)
                     (handle-mouse-event-by-tool event))))))

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
 :highlight-pixels
 (fn [poses]
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))

         source-frame (:source-frame db)
         frame-size (frame/get-size source-frame)
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]

     (. ctx (clearRect 0 0 (:width canvas-size) (:height canvas-size)))

     (doseq [pos poses]
       (set! (. ctx -fillStyle) (->> (frame/get-pixel pos source-frame)
                                     get-highlight-color))
       (. ctx (fillRect (* (:x pos) scale) (* (:y pos) scale) scale scale))))))

(re-frame/reg-fx
 :draw-preview
 (fn [[preview {:keys [clear]}]]
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))

         frame-size (-> db :source-frame frame/get-size)
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]
     (set! (. canvas -width) (:width canvas-size))
     (set! (. canvas -height) (:height canvas-size))

     (when clear
       (. ctx (clearRect 0 0 (:width canvas-size) (:height canvas-size))))

     (doseq [[pos color] preview]
       (set! (. ctx -fillStyle) (or color "white"))
       (. ctx (fillRect (* (:x pos) scale) (* (:y pos) scale) scale scale))))))

(defn draw-pixel-grid [frame-size canvas-size scale ctx]
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
      (.stroke))))

(re-frame/reg-fx
 :draw-frame
 (fn [frame]
   (let [db @re-frame.db/app-db

         canvas (. js/document (getElementById "tutorial"))
         ctx (. canvas (getContext "2d"))

         frame-size (frame/get-size frame)
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]
     (set! (. canvas -width) (:width canvas-size))
     (set! (. canvas -height) (:height canvas-size))

     (doseq [x (range 0 (:width frame-size))
             y (range 0 (:height frame-size))]
       (set! (. ctx -fillStyle) (or (frame/get-pixel {:x x :y y} frame)
                                    "white"))
       (. ctx (fillRect (* x scale) (* y scale) scale scale)))

     (draw-pixel-grid frame-size canvas-size scale ctx))))
