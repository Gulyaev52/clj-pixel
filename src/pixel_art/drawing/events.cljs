(ns pixel-art.drawing.events
  (:require
   [pixel-art.canvas :as canvas]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.tool.core :as tool]
   [pixel-art.tool.utils :refer [get-current-cel]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn [{:keys [db]} [_ event-type mouse-pos right-button]]
   (let [event {:type event-type :pos mouse-pos :right-button right-button}
         tool-events-handlers (tool/get-events-handlers db)]
     (println (-> db :tool :type) tool-events-handlers)
     (case event-type
       :mouse-down
       (let [updated-db (assoc db
                               :initial-mouse-down-pos (:pos event)
                               :mouse-pos (:pos event))
             handler (or (:mouse-down-or-mouse-down-and-move tool-events-handlers)
                         (:mouse-down tool-events-handlers))]
         (println "mouse-down" handler)
         (if handler
           (handler updated-db event)
           {:db updated-db}))

       :mouse-move
       (let [updated-db (assoc db :mouse-pos (:pos event))
             handler (or (when (:initial-mouse-down-pos db)
                           (or (:mouse-down-or-mouse-down-and-move tool-events-handlers)
                               (:mouse-down-and-move tool-events-handlers)))
                         (:mouse-move tool-events-handlers))]
         (if handler
           (handler updated-db event)
           {:db updated-db}))

       :mouse-up
       (let [updated-db (assoc db :mouse-pos (:pos event))
             handler (:mouse-up tool-events-handlers)]
         (if handler
           (-> (handler updated-db event)
               (assoc-in [:db :initial-mouse-down-pos] nil))
           {:db (assoc updated-db :initial-mouse-down-pos nil)}))))))

(re-frame/reg-event-fx
 ::enable-pixels-grid
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc db :pixels-grid-enabled enabled)}))

(re-frame/reg-event-fx
 ::zoom
 (fn [{:keys [db]} [_ delta new-scale center-pos mouse-offset-pos]]
   (let [prev-scale (:scale db)]
     (if (not= prev-scale new-scale)
       (let [delta (if (#{project-settings/max-scale project-settings/min-scale} new-scale)
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

(re-frame/reg-fx
 :draw-preview ;; todo: вынести в fx
 (fn [changes]
   (let [size (-> @db/app-db :sprite :size)
         {transparent-changes true rest-changes false}
         (->> changes
              (filter (fn [[pos]] (geometry/valid-point? pos size)))
              (group-by (fn [[_ color]] (= color color/transparent-color-int))))]
     (canvas/update-image-data (. js/document (getElementById "preview")) rest-changes)
     (when (seq transparent-changes)
       (canvas/update-image-data (. js/document (getElementById "current-layer")) transparent-changes)))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (let [current-cel (get-current-cel @re-frame.db/app-db)
         size (-> @re-frame.db/app-db :sprite :size)
         changes (->> poses
                      (filter (fn [pos] (geometry/valid-point? pos size)))
                      (map (fn [pos] [pos (color/get-highlight-color (cel/get-pixel pos current-cel))])))]
     (canvas/update-image-data (. js/document (getElementById "visual-effects"))
                               changes))))

(re-frame/reg-fx
 :clear-visual-effects
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "visual-effects")))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "preview")))))
