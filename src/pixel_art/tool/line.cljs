(ns pixel-art.tool.line
  (:require
   [pixel-art.model.preview :as preview]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color
                                 get-preview-from-current-cel get-tool-options
                                 resize-pixel]]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (shape/make
   {:type :line
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :straight :label "Straight"})]
    :draw
    (fn draw [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            current-color (get-current-color db event)
            {:keys [pixel-size straight]} (get-tool-options db)
            line-pixels (if straight
                          (geometry/get-uniform-line-pixels initial-mouse-down-pos (:pos event))
                          (geometry/get-line-pixels initial-mouse-down-pos (:pos event)))
            preview (get-preview-from-current-cel db)]
        ;; draw the square ends of the line
        (doseq [{:keys [x y]} (resize-pixel (aget line-pixels 0) pixel-size)]
          (preview/set-color! preview x y current-color))
        (doseq [{:keys [x y]} (resize-pixel (aget line-pixels (- (count line-pixels) 1)) pixel-size)]
          (preview/set-color! preview x y current-color))
        ;; for each step along the line, draw an x centered on that pixel of size penSize
        (doseq [[x y] line-pixels]
          (dotimes [i pixel-size]
            (let [new-x (+ (- x (. js/Math (floor (/ pixel-size 2)))) i)
                  new-y (+ (- y (. js/Math (floor (/ pixel-size 2)))) i)]
              (preview/set-color! preview new-x new-y current-color))
            (let [new-x (+ (- x (. js/Math (floor (/ pixel-size 2)))) i)
                  new-y (- (+ y (. js/Math (floor (/ pixel-size 2)))) i 1)]
              (preview/set-color! preview new-x new-y current-color))
            ;; draw an additional x directly next to the first to prevent unwanted dithering
            (when (not= i 0)
              (let [new-x (+ (- x (. js/Math (floor (/ pixel-size 2)))) i)
                    new-y (- (+ (- y (. js/Math (floor (/ pixel-size 2)))) i) 1)]
                (preview/set-color! preview new-x new-y current-color))
              (let [new-x (+ (- x (. js/Math (floor (/ pixel-size 2)))) i)
                    new-y (- (+ y (. js/Math (floor (/ pixel-size 2)))) i)]
                (preview/set-color! preview new-x new-y current-color)))))
        preview))}))
