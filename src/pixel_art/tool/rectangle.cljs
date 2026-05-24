(ns pixel-art.tool.rectangle
  (:require
   [pixel-art.model.preview :as preview]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color
                                 get-preview-from-current-cel get-tool-options]]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (shape/make
   {:type :rectangle
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :fill :label "Fill"})
                   (options-spec/make-checkbox {:field :keep-ratio :label "Keep ration"})]
    :draw
    (fn [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            current-color (get-current-color db event)
            {:keys [pixel-size fill keep-ratio]} (get-tool-options db)
            current-pos (if keep-ratio
                          (geometry/get-scaled-points initial-mouse-down-pos (:pos event))
                          (:pos event))
            {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points [initial-mouse-down-pos current-pos])
            x-top-left (:x top-left)
            x-bottom-right (:x bottom-right)
            y-top-left (:y top-left)
            y-bottom-right (:y bottom-right)

            preview (get-preview-from-current-cel db)]
        (if fill
          (doseq [x (range x-top-left (inc x-bottom-right))
                  y (range y-top-left (inc y-bottom-right))]
            (preview/set-color! preview x y current-color))
          (doseq [x (range x-top-left (inc x-bottom-right))
                  y (range y-top-left (inc y-bottom-right))]
            (when (or (> x (- x-bottom-right pixel-size))
                      (< x (+ x-top-left pixel-size))
                      (> y (- y-bottom-right pixel-size))
                      (< y (+ y-top-left pixel-size)))
              (preview/set-color! preview x y current-color))))
        preview))}))
