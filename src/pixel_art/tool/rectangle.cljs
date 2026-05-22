(ns pixel-art.tool.rectangle
  (:require
   ["../utils/t.js" :as t]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color get-tool-options]]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (shape/make
   {:type :rectangle
    :options-spec [options-spec/pixel-size]
    :get-points
    (fn [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            current-color (get-current-color db event)
            {:keys [pixel-size]} (get-tool-options db)
            {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points [initial-mouse-down-pos (:pos event)])
            x-top-left (:x top-left)
            x-bottom-right (:x bottom-right)
            y-top-left (:y top-left)
            y-bottom-right (:y bottom-right)]
        (t/fors (clj->js {:i1 x-top-left :i1Stop (inc x-bottom-right)
                          :i2 y-top-left :i2Stop (inc y-bottom-right)
                          :when (fn [x y]
                                  (or (> x (- x-bottom-right pixel-size))
                                      (< x (+ x-top-left pixel-size))
                                      (> y (- y-bottom-right pixel-size))
                                      (< y (+ y-top-left pixel-size))))})
                (fn [x y] [{:x x :y y} current-color]))))}))
