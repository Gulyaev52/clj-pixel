(ns pixel-art.utils.geometry
  (:require
   ["./shapeTool.js" :as shape-tool]
   [clojure.string :as string]))

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

(defn pos->idx
  ([x y size]
   (when (and (< -1 x (:width size))
              (< -1 y (:height size)))
     (+ x (* (:width size) y))))
  ;; more performant version. lookup can be not performant when doing very often. (e.x. grid is 512x512 and we are making selection)
  ([x y width height]
   (when (and (< -1 x width)
              (< -1 y height))
     (+ x (* width y)))))

;; flood fill algorithm
;; f should return true if a point is not visited
(defn visit-connected-pixels [start-pos f]
  (let [queue #js [#js [(:x start-pos) (:y start-pos)]] ;; js data structures here because they are more performant when canvas size large (e.g. 512x512)

        dy #js [-1 0 1 0]
        dx #js [0 1 0 -1]]
    (f (:x start-pos) (:y start-pos))
    (while (> (. queue -length) 0)
      (let [current-item (. queue pop)]
        (dotimes [i 4]
          (let [next-x (+ (aget current-item 0) (aget dx i))
                next-y (+ (aget current-item 1) (aget dy i))
                is-valid (f next-x next-y)]
            (when is-valid
              (let [connected-pixel #js [next-x next-y]]
                (. queue (push connected-pixel))))))))))

(defn get-line-pixels [p1 p2]
  (shape-tool/getLinePixels (:x p1) (:x p2) (:y p1) (:y p2)))

(defn get-uniform-line-pixels [p1 p2]
  (shape-tool/getUniformLinePixels (:x p1) (:x p2) (:y p1) (:y p2)))

(defn get-circle-pixels [p1 p2 pixel-size f]
  (shape-tool/getCirclePixels (:x p1) (:y p1) (:x p2) (:y p2) pixel-size f))

(defn get-scaled-points
  "Transform the coordinates to preserve a square 1:1 ratio from the origin of the shape"
  [initial-pos pos]
  (let [[x y] (shape-tool/getScaledCoords (:x initial-pos) (:y initial-pos)
                                          (:x pos) (:y pos))]
    {:x x :y y}))
(comment (get-scaled-points {:x 0 :y 0} {:x 2 :y 2}))
