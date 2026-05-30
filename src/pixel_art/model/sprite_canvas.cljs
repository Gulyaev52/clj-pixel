(ns pixel-art.model.sprite-canvas 
  (:require
    [pixel-art.model.sprite :as sprite]))

(defn draw-cel
  ([cel canvas]
   (let [ctx (. canvas (getContext "2d"))
         size (:size cel)
         image-data (js/ImageData. (js/Uint8ClampedArray. (. (:pixels cel) -buffer))
                                   (:width size)
                                   (:height size))]
     (. ctx (putImageData image-data 0 0))
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
