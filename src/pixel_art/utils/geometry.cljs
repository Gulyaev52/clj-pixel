(ns pixel-art.utils.geometry
  (:require [clojure.string :as string]))

;; todo: pixels?

(defn get-ordered-rectangle-points [points]
  {:top-left {:x (apply min (map :x points))
              :y (apply min (map :y points))}
   :bottom-right {:x (apply max (map :x points))
                  :y (apply max (map :y points))}})

(defn get-rectange-border-points [p1 p2]
  (let [{:keys [top-left bottom-right]} (get-ordered-rectangle-points [p1 p2])]
    (->> (for [x (range (:x top-left) (inc (:x bottom-right)))]
           (for [y (range (:y top-left) (inc (:y bottom-right)))]
             (if (and (< (:x top-left) x (:x bottom-right))
                      (< (:y top-left) y (:y bottom-right)))
               nil
               {:x x :y y})))
         flatten
         (keep identity))))

(defn get-rectange-points [p1 p2]
  (let [{:keys [top-left bottom-right]} (get-ordered-rectangle-points [p1 p2])]
    (->> (for [x (range (:x top-left) (inc (:x bottom-right)))]
           (for [y (range (:y top-left) (inc (:y bottom-right)))]
             {:x x :y y}))
         flatten)))

(defn display-points [points size]
  (let [{:keys [width]} size
        point-map (->> points
                       (map (fn [point] [[(:x point) (:y point)] true]))
                       (into {}))]
    (->> (repeat (* (:width size) (:height size)) nil)
         (partition width)
         (map-indexed (fn [y row]
                        (->> row
                             (map-indexed (fn [x _] (if (get point-map [x y])
                                                      "m"
                                                      "_")))
                             (string/join " "))))
         (string/join "\n")
         println)))

(defn valid-point? [{:keys [x y]} {:keys [width height]}]
  (and (and (>= x 0) (< x width))
       (and (>= y 0) (< y height))))
