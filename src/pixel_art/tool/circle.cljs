(ns pixel-art.tool.circle
  (:require
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color get-tool-options]]
   [pixel-art.utils.geometry :as geometry]
   [pixel-art.tool.options-spec :as options-spec]))

(defn- get-filled-circle-points [pos1 pos2 pixel-size]
  (->> (geometry/get-circle-pixels pos1 pos2 pixel-size)
       (partition-all 2)
       (filter (fn [[x1 x2]] (not= x1 x2)))
       (mapcat (fn [[x1 x2]]
                 (geometry/get-rectange-points x1 x2)))))

(defn- draw [db event]
  (let [{:keys [initial-mouse-down-pos]} db
        current-color (get-current-color db event)
        {:keys [pixel-size fill keep-ratio]} (get-tool-options db)
        current-pos (if keep-ratio
                      (geometry/get-scaled-points initial-mouse-down-pos (:pos event))
                      (:pos event))
        circle-points (if fill
                        (get-filled-circle-points initial-mouse-down-pos current-pos pixel-size)
                        (geometry/get-circle-pixels initial-mouse-down-pos current-pos pixel-size))]
    (->> circle-points
         (map (fn [p] [p current-color])))))

(def tool
  (shape/make
   {:type :circle
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :fill :label "Fill"})
                   (options-spec/make-checkbox {:field :keep-ratio :label "Keep ration"})]
    :draw draw}))
