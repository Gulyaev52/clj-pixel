(ns pixel-art.drawing.events
  (:require
   [pixel-art.project-config :as project-config]
   [pixel-art.tool.core :as tool]
   [re-frame.core :as re-frame]))

(defn run-events-handlers [events events-handlers db event]
  (->> (keep #(get events-handlers %) events)
       (reduce (fn [res h]
                 (let [new-res (h (:db res) event)]
                   {:db (:db new-res)
                    :fx (into (:fx res) (:fx new-res))}))
               {:db db :fx []})))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn [{:keys [db]} [_ event-type mouse-pos right-button]]
   (-> (let [event {:type event-type :pos mouse-pos :right-button right-button}
             tool-events-handlers (tool/get-events-handlers db)]
         (case event-type
           :mouse-down
           (let [updated-db (assoc db
                                   :initial-mouse-down-pos (:pos event)
                                   :mouse-pos (:pos event))] ;; todo: зачем это надо
             (run-events-handlers [:mouse-down :mouse-down-or-mouse-down-and-move] tool-events-handlers updated-db event))

           :mouse-move
           (let [updated-db (assoc db :mouse-pos (:pos event))]
             (run-events-handlers
              (concat []
                      (when (not (:initial-mouse-down-pos updated-db))
                        [:mouse-move-without-mouse-down])
                      (when (:initial-mouse-down-pos updated-db)
                        [:mouse-down-or-mouse-down-and-move :mouse-down-and-move]))
              tool-events-handlers
              updated-db
              event))

           :mouse-up
           (let [updated-db (assoc db :mouse-pos (:pos event))]
             (-> (run-events-handlers [:mouse-up]
                                      tool-events-handlers
                                      updated-db
                                      event)
                 (assoc-in [:db :initial-mouse-down-pos] nil)))))
       (assoc-in [:db :prev-pos] mouse-pos))))

(re-frame/reg-event-fx
 ::enable-pixels-grid
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc db :pixels-grid-enabled enabled)}))

(re-frame/reg-event-fx
 ::zoom
 (fn [{:keys [db]} [_ delta new-scale center-pos mouse-offset-pos]]
   (let [prev-scale (:scale db)]
     (if (not= prev-scale new-scale)
       (let [delta (if (#{project-config/max-zoom-scale project-config/min-zoom-scale} new-scale)
                     (/ new-scale prev-scale)
                     delta)
             old-canvas-size (-> db :sprite :size (update-vals #(* % (:scale db))))
             new-canvas-size (-> db :sprite :size (update-vals #(* % new-scale)))
             old-drawing-container-size (:drawing-container-size db)
             canvas-size-diff (merge-with - new-canvas-size old-canvas-size)
             new-drawing-container-size (merge-with + old-drawing-container-size canvas-size-diff)
             old-canvas-pos {:x (- (/ (:width old-drawing-container-size) 2)
                                   (/ (:width old-canvas-size) 2))
                             :y (- (/ (:height old-drawing-container-size) 2)
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
                  (assoc :drawing-container-size new-drawing-container-size))})
       {:db db}))))

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
     {:db (assoc db :viewport-scroll new-viewport-scroll)})))

(re-frame/reg-event-fx
 ::stop-panning
 (fn [{:keys [db]} [_]]
   {:db (assoc db
               :start-panning-pos nil
               :initial-viewport-scroll nil)}))