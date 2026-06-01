(ns pixel-art.sprite-resizer.utils
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
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
    (let [cel-size (:size cel)
          translated-pixels-map
          (cel/map-pixels (fn [pos color]
                            (when (not= color color/transparent-color-int)
                              [{:x (translate-x (:x pos) (:width cel-size) (:width target-size) (:x anchor))
                                :y (translate-y (:y pos) (:height cel-size) (:height target-size) (:y anchor))}
                               color]))
                          cel)
          new-pixels (->> (cel/create-pixels-coll target-size)
                          (cel/update-pixels-coll translated-pixels-map target-size))]
      (assoc cel
             :size target-size
             :pixels new-pixels))))

(defn resize-sprite [sprite settings]
  (let [resized-cels (coll/map-matrix #(resize-cel %1 settings) (:cels sprite))]
    (assoc sprite
           :cels resized-cels
           :size (:target-size settings))))
