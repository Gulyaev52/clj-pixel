(ns pixel-art.canvas
  (:require [pixel-art.model.frame :as frame]))

(defn draw-frame [frame scale canvas]
  (let [ctx (. canvas (getContext "2d"))
        frame-size (frame/get-size frame)]
    (doseq [x (range 0 (:width frame-size))
            y (range 0 (:height frame-size))]
      (when-let [color (frame/get-pixel {:x x :y y} frame)]
        (set! (. ctx -fillStyle) color)
        (. ctx (fillRect (* x scale) (* y scale) scale scale))))))

(defn draw-on-zoomed-canvas [canvas db draw]
  (let [ctx (. canvas (getContext "2d"))
        scale (:scale db)
        panning-pos (:panning-pos db)]
    (. ctx save)
    (.. ctx (translate (:x panning-pos) (:y panning-pos)))
    (.. ctx (scale scale scale))
    (draw {:canvas canvas :ctx ctx})
    (. ctx restore)))

(defn clear-canvas [canvas]
  (let [ctx (. canvas (getContext "2d"))]
    (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))))
