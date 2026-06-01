(ns pixel-art.model.sprite-canvas
  (:require
    [pixel-art.model.sprite :as sprite]
    [pixel-art.utils.canvas :as canvas]))

(defn draw-cel
  "Draws a cel onto `canvas` with alpha blending so transparent pixels keep
  whatever is already on the canvas visible.

  We can't just call ctx.putImageData: putImageData fully replaces the target
  pixels (including transparent ones) and ignores the compositing mode, so
  stacking cels on one canvas would let an upper cel's transparent pixels erase
  the cel below. Instead we putImageData the cel onto a throwaway offscreen
  canvas (nothing to erase there), then drawImage it over the target, which uses
  the default source-over mode and blends by alpha.

  Note: drawImage does not clear the target. When the same canvas is repainted
  repeatedly (e.g. a tool preview while dragging), clear it first with
  canvas/clear-canvas, otherwise earlier frames accumulate."
  ([cel canvas]
   (let [ctx (. canvas (getContext "2d"))
         size (:size cel)
         image-data (js/ImageData. (js/Uint8ClampedArray. (. (:pixels cel) -buffer))
                                   (:width size)
                                   (:height size))
         offscreen (canvas/create-canvas size)]
     (. (. offscreen (getContext "2d")) (putImageData image-data 0 0))
     (. ctx (drawImage offscreen 0 0))
     canvas)))

(defn draw-frame [frame-idx sprite]
  (let [{:keys [layer-idx]} (sprite/get-current-cel-pos sprite)
        cels (sprite/get-frame-cels-with-layers frame-idx sprite)
        cels-above [(reverse (take layer-idx cels)) (. js/document (getElementById "layers-above"))]
        cels-below [(reverse (drop (inc layer-idx) cels)) (. js/document (getElementById "layers-below"))]
        current-cel [[(nth cels layer-idx)] (. js/document (getElementById "current-layer"))]]
    (doseq [[cels canvas] [cels-above cels-below current-cel]]
      (doseq [cel cels]
        (when (-> cel :layer :visible?)
          (draw-cel cel canvas))))))

(defn draw-frame-on-single-canvas [frame-idx sprite canvas]
  (let [cels (sprite/get-frame-cels-with-layers frame-idx sprite)]
    (doseq [cel (reverse cels)]
      (when (-> cel :layer :visible?)
        (draw-cel cel canvas))))
  canvas)

(defn draw-cels-on-single-canvas [cels canvas]
  (doseq [cel (reverse cels)]
    (draw-cel cel canvas))
  canvas)
