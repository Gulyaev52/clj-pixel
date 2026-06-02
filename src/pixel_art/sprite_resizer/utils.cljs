(ns pixel-art.sprite-resizer.utils
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.sprite-canvas :as sprite-canvas]
   [pixel-art.utils.canvas :as canvas]
   [pixel-art.utils.coll :as coll]))

(defn- translate-x [x width resized-width anchor-x]
  (case anchor-x
    :left x
    :right (- x (- width resized-width))
    :center (- x (. js/Math (round (/ (- width resized-width) 2))))))

(defn- translate-y [y height resized-height anchor-y]
  (case anchor-y
    :top y
    :bottom (- y (- height resized-height))
    :center (- y (. js/Math (round (/ (- height resized-height) 2))))))

(defn- resize-cel [cel {:keys [target-size resize-content anchor]}]
  (if resize-content
    (->> (canvas/create-canvas (:size cel))
         (sprite-canvas/draw-cel cel)
         (canvas/resize target-size)
         ((fn [canvas]
            (assoc cel
                   :size target-size
                   :pixels (canvas/canvas->pixels canvas target-size)))))
    ;; resize-content=false is a pure translation/crop: every pixel moves by the
    ;; same offset (dx, dy) = (translate-x 0 ...), (translate-y 0 ...). So we copy
    ;; the overlapping rectangle row-by-row with native Uint32Array set/subarray —
    ;; no per-pixel maps, no intermediate seq (fast on 512x512).
    (let [{source-width :width source-height :height} (:size cel)
          {target-width :width target-height :height} target-size
          dx (translate-x 0 source-width target-width (:x anchor))
          dy (translate-y 0 source-height target-height (:y anchor))
          ^js src (:pixels cel)
          ^js dst (cel/create-pixels-coll target-size)
          x-start (max 0 (- dx))                ;; source columns that land in [0, tw)
          x-end   (min source-width (- target-width dx))
          cols    (- x-end x-start)
          y-start (max 0 (- dy))                ;; source rows that land in [0, th)
          y-end   (min source-height (- target-height dy))]
      (when (and (pos? cols) (< y-start y-end))
        (loop [y y-start]
          (when (< y y-end)
            (let [src-off (+ (* y source-width) x-start)
                  dst-off (+ (* (+ y dy) target-width) (+ x-start dx))]
              (.set dst (.subarray src src-off (+ src-off cols)) dst-off))
            (recur (inc y)))))
      (assoc cel
             :size target-size
             :pixels dst))))

(defn resize-sprite [sprite settings]
  (if (not= (:size sprite) (:target-size settings))
    (let [resized-cels (coll/map-matrix #(resize-cel %1 settings) (:cels sprite))]
      (assoc sprite
             :cels resized-cels
             :size (:target-size settings)))
    sprite))
