(ns pixel-art.model.frame
  (:require [clojure.string :as string]))

(def transparent-color nil)

(defn create [size]
  {:pixels (vec (repeat (* (:width size) (:height size)) transparent-color))
   :size size})

(defn resize [size frame])

(defn- pos->idx [pos size]
  (let [y (->> (* (:width size) (:y pos))
               (#(if (< % 0) 0 %)))]
    (+ (:x pos) y)))

(defn set-pixels [pixels-with-coords frame]
  (let [{:keys [pixels size]} frame]
    {:pixels (reduce (fn [res-pixels {:keys [pos color]}]
                       (assoc res-pixels (pos->idx pos size) color))
                     pixels
                     pixels-with-coords)
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
                             (map (fn [x] (if x (nth x 0) "x")))
                             (string/join ""))))
         (string/join "\n")
         println)))
