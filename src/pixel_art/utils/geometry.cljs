(ns pixel-art.utils.geometry
  (:require [clojure.string :as string]))

;; todo: pixels?

(defn get-ordered-rectangle-points [p1 p2]
  {:top-left {:x (min (:x p1) (:x p2))
              :y (min (:y p1) (:y p2))}
   :bottom-right {:x (max (:x p1) (:x p2))
                  :y (max (:y p1) (:y p2))}})

(defn get-rectange-border-points [p1 p2]
  (let [{:keys [top-left bottom-right]} (get-ordered-rectangle-points p1 p2)]
    (->> (for [x (range (:x top-left) (inc (:x bottom-right)))]
           (for [y (range (:y top-left) (inc (:y bottom-right)))]
             (if (and (< (:x top-left) x (:x bottom-right))
                      (< (:y top-left) y (:y bottom-right)))
               nil
               {:x x :y y})))
         flatten
         (keep identity))))

(defn get-rectange-points [p1 p2]
  (let [{:keys [top-left bottom-right]} (get-ordered-rectangle-points p1 p2)]
    (->> (for [x (range (:x top-left) (inc (:x bottom-right)))]
           (for [y (range (:y top-left) (inc (:y bottom-right)))]
             {:x x :y y}))
         flatten)))

;; todo: rename?
(defn get-rectange-top-left-and-bottom-right [points]
  (let [sorted (->> points (sort-by :x) (sort-by :y))]
    {:top-left (first sorted)
     :bottom-right (last sorted)}))

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
