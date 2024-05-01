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

;; todo: remove
(defn set-pixels [pixels-with-pos frame]
  (let [{:keys [pixels size]} frame]
    {:pixels (reduce (fn [res-pixels {:keys [pos color]}]
                       (if (valid-pos? pos size)
                         (assoc res-pixels (pos->idx pos size) color)
                         res-pixels))
                     pixels
                     pixels-with-pos)
     :size size}))

(defn set-pixels-map [pixels-map frame]
  (let [{:keys [pixels size]} frame]
    {:pixels (reduce (fn [res-pixels [pos color]]
                       (if (valid-pos? pos size)
                         (assoc res-pixels (pos->idx pos size) color)
                         res-pixels))
                     pixels
                     pixels-map)
     :size size}))

(def get-size :size)

(defn get-pixel [pos frame]
  (let [{:keys [pixels size]} frame]
    (nth pixels (pos->idx pos size) transparent-color)))

(defn display-frame [frame]
  (let [{:keys [width]} (:size frame)]
    (->> (:pixels frame)
         (partition width)
         (map (fn [row] (->> row
                             (map (fn [x] (if x (nth x 0) "_")))
                             (string/join " "))))
         (string/join "\n")
         println)))

(defn display-pixels [pixels size]
  (let [{:keys [width]} size]
    (->> pixels
         (partition width)
         (map (fn [row] (->> row
                             (map (fn [{:keys [color]}] (if color (nth color 0) "_")))
                             (string/join " "))))
         (string/join "\n")
         println)))
