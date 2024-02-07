(ns pixel-art.events
  (:require [day8.re-frame.tracing :refer [fn-traced]]
            [pixel-art.db :as db]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.pen :as pen]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [re-frame.core :as re-frame]
            [re-frame.db]
            [sc.api :as api]))

(re-frame/reg-event-fx
 ::initialize-db
 (fn [_ _]
   {:db db/default-db}))

(re-frame/reg-event-fx
 ::initialize-canvas
 (fn [{:keys [db]} _]
   {:draw-frame (:source-frame db)}))

(re-frame/reg-event-db
 ::select-tool
 (fn [db [_ tool-type]]
   (assoc db :tool {:type tool-type})))

(defn run-behaviour [event data]
  (case (-> data :tool :type)
    :pen (pen/behaviour event data)
    :rectangle (rectangle/behaviour event data)
    :rectangle-select (rectangle-select/behaviour event data)))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn-traced [{:keys [db]} [_ event-type mouse-pos]]
            (println event-type mouse-pos)
            db
            (if (and (#{:mouse-move :mouse-up} event-type)
                     (not (:initial-mouse-down-pos db)))
              {:db db}
              (let [behaviour-res (run-behaviour {:type event-type :pos mouse-pos}
                                                 (assoc db
                                                        :initial-mouse-down-pos
                                                        (or (:initial-mouse-down-pos db) mouse-pos)))
                    new-overlay-frame (or (:overlay-frame behaviour-res) (:overlay-frame db))]
                (merge {:db (merge db
                                   (when (= :mouse-down event-type)
                                     {:initial-mouse-down-pos mouse-pos})
                                   (when (= :mouse-up event-type)
                                     {:initial-mouse-down-pos nil})
                                   {:overlay-frame new-overlay-frame
                                    :tool (or (:tool behaviour-res) (:tool db))}
                                   (when (:commit-changes behaviour-res)
                                     {:source-frame (or (:overlay-frame behaviour-res) (:overlay-frame db))}))
                        :draw-frame new-overlay-frame}
                       (:effects behaviour-res))))))

#_(api/last-ep-id)
#_(defsc [709 -83])
#_(pixel-art.events.event-collector/repeat-last-event)
#_(re-frame/dispatch (second @pixel-art.events.event-collector/event-store))

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
