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

(defn canvas-pos->frame-pos [event scale canvas]
  (let [rect (. canvas getBoundingClientRect)]
    {:x (. js/Math (floor (/ (- (. event -clientX) (. rect -left))
                             scale)))
     :y (. js/Math (floor (/ (- (. event -clientY) (. rect -top))
                             scale)))}))

(re-frame/reg-event-fx
 ::initialize-db
 (fn [_ _]
   {:db db/default-db}))

(re-frame/reg-event-fx
 ::initialize-canvas
 (fn [{:keys [db]} _]
   {:draw-frame (:frame db)}))

(re-frame/reg-event-db
 ::select-tool
 (fn [db [_ tool-type]]
   (assoc db :tool {:type tool-type})))

(defn run-behaviour [data]
  (case (-> data :tool :type)
    :pen (pen/behaviour data)
    :rectangle (rectangle/behaviour data)
    :rectangle-select (rectangle-select/behaviour data)))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn-traced [{:keys [db]} [_ event-type origin-event]]
            (if (and (#{:mouse-move :mouse-up} event-type)
                     (not (:initial-mouse-down-pos db)))
              {:db db}
              (let [mouse-pos (canvas-pos->frame-pos origin-event
                                                     (:scale db)
                                                     (. js/document (getElementById "tutorial")))
                    {:keys [overlay-frame frame color tool]} db
                    behaviour-res (run-behaviour {:source-frame frame
                                                  :overlay-frame overlay-frame
                                                  :color color
                                                  :tool tool
                                                  :event {:type event-type :pos mouse-pos}})
                    new-overlay-frame (or (:overlay-frame behaviour-res) overlay-frame)]
                (println event-type)
                {:db (merge db
                            (when (= :mouse-down event-type)
                              {:initial-mouse-down-pos mouse-pos})
                            (when (= :mouse-up event-type)
                              {:initial-mouse-down-pos nil})
                            {:overlay-frame new-overlay-frame
                             :tool (or (:tool behaviour-res) tool)}
                            (when (:commit-changes behaviour-res)
                              {:frame (or (:overlay-frame behaviour-res) overlay-frame)}))
                 :draw-frame new-overlay-frame}))))

;; (dissoc (:state (:tool @re-frame.db/app-db)) :initial-selection :selection)
;; @pixel-art.events.event-collector/event-store

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
