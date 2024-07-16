(ns pixel-art.canvas
  (:require ["tinycolor2" :as tinycolor]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.sprite :as sprite]
            [sc.api]))

(defn generate-img [draw size]
  (let [canvas-elem (.. js/document (createElement "canvas"))]
    (set! (. canvas-elem -width) (:width size))
    (set! (. canvas-elem -height) (:height size))
    (draw canvas-elem)
    (. canvas-elem (toDataURL "image/png"))))

(defn draw-frame [frame-idx sprite]
  (let [size (sprite/get-size sprite)
        cels (sprite/get-frame-cels-with-layers frame-idx sprite)
        layers-canvases (reverse (vec (. js/document (getElementsByClassName "layer"))))]
    (doseq [[cel canvas] (map vector (reverse cels) layers-canvases)]
      (let [ctx (. canvas (getContext "2d"))]
        (when (-> cel :layer :visibile?)
          (doseq [x (range 0 (:width size))
                  y (range 0 (:height size))]
            (when-let [color (cel/get-pixel {:x x :y y} cel)]
              (set! (. ctx -fillStyle) (.. (tinycolor color)
                                           (setAlpha (:opacity cel))
                                           (toRgbString)))
              (. ctx (fillRect x y 1 1)))))))))

(defn draw-frame-on-single-canvas [frame-idx sprite canvas]
  (let [ctx (. canvas (getContext "2d"))
        size (sprite/get-size sprite)
        cels (sprite/get-frame-cels-with-layers frame-idx sprite)]
    (doseq [cel (reverse cels)]
      (when (-> cel :layer :visibile?)
        (doseq [x (range 0 (:width size))
                y (range 0 (:height size))]
          (when-let [color (cel/get-pixel {:x x :y y} cel)]
            (set! (. ctx -fillStyle) (.. (tinycolor color)
                                         (setAlpha (:opacity cel))
                                         (toRgbString)))
            (. ctx (fillRect x y 1 1))))))))

(defn draw-cel [cel canvas]
  (let [ctx (. canvas (getContext "2d"))
        size (cel/get-size cel)]
    (doseq [x (range 0 (:width size))
            y (range 0 (:height size))]
      (when-let [color (cel/get-pixel {:x x :y y} cel)]
        (set! (. ctx -fillStyle) (.. (tinycolor color)
                                     (setAlpha (:opacity cel))
                                     (toRgbString)))
        (. ctx (fillRect x y 1 1))))))

(defn clear-canvas [canvas]
  (let [ctx (. canvas (getContext "2d"))]
    (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))))

(defn clear-canvases [canvases]
  (doseq [canvas canvases]
    (clear-canvas canvas)))

(defn get-canvas-context [id]
  (let [canvas (. js/document (getElementById id))]
    (.. canvas (getContext "2d"))))
