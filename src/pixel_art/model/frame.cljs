(ns pixel-art.model.frame
  (:require [clojure.string :as string]))

(def transparent-color nil)

(defn- pos->idx [{:keys [x y]} {:keys [width]}]
  (+ x (* width y)))

(defn- valid-pos? [{:keys [x y]} {:keys [width height]}]
  (and (and (>= x 0) (< x width))
       (and (>= y 0) (< y height))))

(defn create [size]
  {:pixels (vec (repeat (* (:width size) (:height size)) transparent-color))
   :size size})

(defn resize [size frame])

(defn set-pixels [pixels-with-pos frame]
  (let [{:keys [pixels size]} frame]
    {:pixels (reduce (fn [res-pixels {:keys [pos color]}]
                       (if (valid-pos? pos size)
                         (assoc res-pixels (pos->idx pos size) color)
                         res-pixels))
                     pixels
                     pixels-with-pos)
     :size size}))

(defn get-pixel [pos frame]
  (let [{:keys [pixels size]} frame]
    (nth pixels (pos->idx pos size) transparent-color)))

(defn get-pixels [coll-pos frame]
  (map #(get-pixel % frame) coll-pos))

(defn display-frame [frame]
  (let [{:keys [width]} (:size frame)]
    (->> (:pixels frame)
         (partition width)
         (map (fn [row] (->> row
                             (map (fn [x] (if x (nth x 0) "t")))
                             (string/join ""))))
         (string/join "\n")
         println)))
