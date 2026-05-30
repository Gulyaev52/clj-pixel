(ns pixel-art.drawing.initial-settings 
  (:require
    [pixel-art.project-settings :refer [max-zoom-scale min-zoom-scale]]))

(defn get-viewport-scroll-pos-to-center [canvas-size drawing-container-size viewport-size]
  (let [scroll-dim 15
        canvas-offset-y (/ (:height drawing-container-size) 2)
        absolute-canvas-y (+ canvas-offset-y (/ (:height canvas-size) 2))
        canvas-y-middle (- absolute-canvas-y
                           (/ (- (:height viewport-size) scroll-dim) 2)
                           (/ (:height canvas-size) 2))
        canvas-offset-x (/ (:width drawing-container-size) 2)
        absolute-canvas-x (+ canvas-offset-x (/ (:width canvas-size) 2))
        canvas-x-middle (- absolute-canvas-x
                           (/ (- (:width viewport-size) scroll-dim) 2)
                           (/ (:width canvas-size) 2))]
    {:x canvas-x-middle :y canvas-y-middle}))

(defn get-initial-scale [sprite-size viewport-size]
  (let [[dim dim-value] (apply max-key second sprite-size)
        offset 100]
    (-> (/ (- (dim viewport-size) offset) dim-value)
        (min max-zoom-scale)
        (max min-zoom-scale))))

;; todo: rename
;; todo: а надо ли это? мб просто вызвать scrollIntoView
;; todo: в drawing?
(defn get-initial-drawing-settings [sprite-size viewport-size]
  (let [scale (get-initial-scale sprite-size viewport-size)
        canvas-size (update-vals sprite-size #(* % scale))
        empty-space-dim (max 1500 (:width viewport-size))
        drawing-container-size (update-vals canvas-size #(+ % empty-space-dim))
        viewport-scroll (get-viewport-scroll-pos-to-center canvas-size drawing-container-size viewport-size)]
    {:scale scale
     :drawing-container-size drawing-container-size
     :viewport-scroll viewport-scroll
     :viewport-size viewport-size}))
