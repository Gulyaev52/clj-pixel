(ns pixel-art.utils.geometry
  (:require ["../shapeTool.js" :as shape-tool]
            [clojure.string :as string]))

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

(defn flood-fill [start-point size pred]
  (let [!fill-stack (atom [start-point])
        !visited-points (atom #{})]
    (while (> (count @!fill-stack) 0)
      (let [point (first @!fill-stack)]
        (swap! !fill-stack #(drop 1 %))
        (when (and (valid-point? point size)
                   (not (@!visited-points point))
                   (pred point))
          (swap! !visited-points conj point)
          (swap! !fill-stack concat [{:x (inc (:x point))
                                      :y (:y point)}
                                     {:x (dec (:x point))
                                      :y (:y point)}
                                     {:x (:x point)
                                      :y (inc (:y point))}
                                     {:x (:x point)
                                      :y (dec (:y point))}]))))
    @!visited-points))
(comment
  (def matrix {{:x 0 :y 0} "black" {:x 1 :y 0} "black" {:x 2 :y 0} "white"
               {:x 0 :y 1} "white" {:x 1 :y 1} "white" {:x 2 :y 1} "white"})
  (flood-fill {:x 0 :y 0} {:width 8 :height 8} #(= (get matrix %) "black")))

(defn get-line-pixels [p1 p2]
  (-> (shape-tool/getLinePixels (:x p1) (:x p2) (:y p1) (:y p2))
      (js->clj :keywordize-keys true)
      (#(map (fn [{:keys [col row]}] {:x col :y row}) %))))

(defn get-uniform-line-pixels [p1 p2]
  (-> (shape-tool/getUniformLinePixels (:x p1) (:x p2) (:y p1) (:y p2))
      (js->clj :keywordize-keys true)
      (#(map (fn [{:keys [col row]}] {:x col :y row}) %))))
