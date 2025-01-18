(ns pixel-art.tool.line
  (:require [pixel-art.tool.utils :refer [get-current-color get-tool-options
                                          resize-pixel]]
            [pixel-art.utils.geometry :as geometry]
            [pixel-art.tool.shape :as shape]
            [pixel-art.tool.options-spec :as options-spec]))

(defn- draw [db event]
  (let [{:keys [initial-mouse-down-pos]} db
        current-color (get-current-color db event)
        {:keys [pixel-size straight]} (get-tool-options db)
        line-points (if straight
                      (geometry/get-uniform-line-pixels initial-mouse-down-pos (:pos event))
                      (geometry/get-line-pixels initial-mouse-down-pos (:pos event)))]
    (->> line-points
         (mapcat #(resize-pixel % pixel-size)) ;; todo: resize точно правильный? https://github.com/piskelapp/piskel/blob/21b8bdd0f3602c455e89f25fb337068fd9ea3a35/src/js/tools/drawing/Stroke.js#L101
         (map (fn [p] [p current-color])))))

(def tool
  (shape/make
   {:type :line
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :straight :label "Straight"})]
    :draw draw}))
