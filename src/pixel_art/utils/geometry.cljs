(ns pixel-art.utils.geometry
  (:require
   ["./shapeTool.js" :as shape-tool]
   [clojure.string :as string]))

;; todo: pixels?

(defn get-ordered-rectangle-points [points]
  {:top-left {:x (apply min (map :x points))
              :y (apply min (map :y points))}
   :bottom-right {:x (apply max (map :x points))
                  :y (apply max (map :y points))}})

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

(defn valid-point?
  ([{:keys [x y]} {:keys [width height]}]
   (and x y (>= x 0) (< x width) (>= y 0) (< y height)))
  ([x y {:keys [width height]}]
   (and x y (>= x 0) (< x width) (>= y 0) (< y height))))

(defn pos->idx [x y width]
  (when (< x width) (+ x (* width y))))

(defn flood-fill [start-pos size pixels target-color]
  (let [{:keys [width height]} size

        queue #js [start-pos]
        visited-pixels #js []
        _ (aset visited-pixels (pos->idx (:x start-pos) (:y start-pos) width) start-pos)

        dy #js [-1 0 1 0]
        dx #js [0 1 0 -1]]

    (while (> (. queue -length) 0)
      (let [current-item (. queue pop)]
        (dotimes [i 4]
          (let [next-x (+ (:x current-item) (aget dx i))
                next-y (+ (:y current-item) (aget dy i))
                idx (if (and (>= next-x 0) (< next-x width) (>= next-y 0) (< next-y height))
                      (pos->idx next-x next-y width)
                      nil)
                is-valid (and idx (not (aget visited-pixels idx)) (= (nth pixels idx nil) target-color))]
            (when is-valid
              (let [connected-pixel {:x next-x :y next-y}]
                (. queue (push connected-pixel))
                (aset visited-pixels idx connected-pixel)))))))
    (. visited-pixels (filter js/Boolean))))

(defn get-line-pixels [p1 p2]
  (-> (shape-tool/getLinePixels (:x p1) (:x p2) (:y p1) (:y p2))
      (js->clj :keywordize-keys true)
      (#(map (fn [{:keys [col row]}] {:x col :y row}) %))))

(defn get-uniform-line-pixels [p1 p2]
  (-> (shape-tool/getUniformLinePixels (:x p1) (:x p2) (:y p1) (:y p2))
      (js->clj :keywordize-keys true)
      (#(map (fn [{:keys [col row]}] {:x col :y row}) %))))

(defn get-scaled-points
  "Transform the coordinates to preserve a square 1:1 ratio from the origin of the shape"
  [initial-pos pos]
  (-> (shape-tool/getScaledCoords (:x initial-pos) (:y initial-pos)
                                  (:x pos) (:y pos))
      (js->clj :keywordize-keys true)
      ((fn [{:keys [col row]}] {:x col :y row}))))
(comment (get-scaled-points {:x 0 :y 0} {:x 2 :y 2}))
