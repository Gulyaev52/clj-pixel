(ns pixel-art.tool.rectangle
  (:require
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color get-tool-options]]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (shape/make
   {:type :rectangle
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :fill :label "Fill"})
                   (options-spec/make-checkbox {:field :keep-ratio :label "Keep ration"})]
    :get-points
    (fn [db event]
      (let [canvas (. js/document (getElementById "preview"))
            size {:width (.-width canvas) :height (.-height canvas)}
            size-width (:width size)
            arr32 (js/Uint32Array. (* 512 512))

            {:keys [initial-mouse-down-pos]} db
            current-color (get-current-color db event)
            {:keys [pixel-size fill]} (get-tool-options db)
            {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points [initial-mouse-down-pos (:pos event)])
            x-top-left (:x top-left)
            x-bottom-right (:x bottom-right)
            y-top-left (:y top-left)
            y-bottom-right (:y bottom-right)

            changes #js []
            preview-copy (. arr32 slice)]
        (if fill
          (doseq [x (range x-top-left (inc x-bottom-right))
                  y (range y-top-left (inc y-bottom-right))]
            (let [idx (geometry/pos->idx x y size-width)]
              (. changes (push #js [idx current-color]))
              (aset preview-copy idx current-color)))
          (doseq [x (range x-top-left (inc x-bottom-right))
                  y (range y-top-left (inc y-bottom-right))]
            (when (or (> x (- x-bottom-right pixel-size))
                      (< x (+ x-top-left pixel-size))
                      (> y (- y-bottom-right pixel-size))
                      (< y (+ y-top-left pixel-size)))
              (aset preview-copy (geometry/pos->idx x y size-width) current-color))))
        preview-copy))}))