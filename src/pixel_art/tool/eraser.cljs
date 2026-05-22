(ns pixel-art.tool.eraser
  (:require
   [pixel-art.model.color :refer [transparent-color-int]]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.simple-pen :as simple-pen]
   [pixel-art.tool.utils :refer [get-tool-options resize-pixel]]
   [pixel-art.utils.geometry :as geometry]))

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

(def tool
  (simple-pen/make
   {:type :eraser
    :options-spec [options-spec/pixel-size]
    :get-points
    (fn [db event]
      (let [{:keys [tool]} db
            {:keys [pixel-size]} (get-tool-options db)
            pos (:pos event)]
        (->>
         (get-interpolated-pixels (-> tool :state :prev-pos) pos)
         (mapcat #(resize-pixel % pixel-size))
         (map (fn [p] [p transparent-color-int])))))}))
