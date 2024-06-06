(ns pixel-art.model.cel
  (:require [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.utils.geometry :as geometry]))

(defn create [size]
  {:pixels (vec (repeat (* (:width size) (:height size)) transparent-color))
   :size size
   :opacity 1})

(defn- pos->idx [{:keys [x y]} {:keys [width]}]
  (+ x (* width y)))

(defn resize [size cel])

(defn set-pixels [pixels-map cel]
  (let [{:keys [pixels size]} cel]
    {:pixels (reduce (fn [res-pixels [pos color]]
                       (if (geometry/valid-point? pos size)
                         (assoc res-pixels (pos->idx pos size) color)
                         res-pixels))
                     pixels
                     pixels-map)
     :size size}))

(defn emptyy? [cel]
  (every? #{transparent-color} (:pixels cel)))

(def get-size :size)

(defn get-pixel [pos cel]
  (let [{:keys [pixels size]} cel]
    (nth pixels (pos->idx pos size) transparent-color)))
