(ns pixel-art.tool.simple-pen
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool2
                                 get-preview-or-create-from-current-cel
                                 get-tool-options resize-pixel
                                 with-highlight-cel-under-cursor]]
   [pixel-art.utils.geometry :as geometry]
   [pixel-art.model.preview :as preview])) ;; todo: rename

(defn- get-interpolated-pixels
  "The pen movement is too fast for the mousemove frequency, there is a gap between the
   current point and the previously drawn one.
   We fill the gap by calculating missing dots (simple linear interpolation) and draw them."
  [prev-pos pos]
  (if (and prev-pos
           (or (> (. js/Math (abs (- (:x pos) (:x prev-pos)))) 1)
               (> (. js/Math (abs (- (:y pos) (:y prev-pos)))) 1)))
    (geometry/get-line-pixels prev-pos pos)
    [pos]))

(defn make [{:keys [type options-spec get-color]}]
  (let [init (fn init [] {:type type
                          :state {:prev-pos nil}})]
    {:type type
     :init init
     :options-spec options-spec
     :get-events-handlers
     (fn []
       (with-highlight-cel-under-cursor
         {:mouse-down-or-mouse-down-and-move
          (fn [db event]
            (let [prev-pos (-> db :tool :state :prev-pos)
                  {:keys [pixel-size]} (get-tool-options db)
                  points (->>
                          (get-interpolated-pixels prev-pos (:pos event))
                          (mapcat #(resize-pixel % pixel-size)))
                  preview (get-preview-or-create-from-current-cel db)]
              (doseq [{:keys [x y]} points]
                (let [color (get-color db event)]
                  (preview/set-color! preview x y color)))
              {:db (-> db
                       (assoc-in [:tool :state] {:prev-pos (:pos event)})
                       (assoc :preview preview))}))
          :mouse-up (fn [db]
                      (commit-changes-and-init-tool2 db (:preview db) (init)))}))}))
