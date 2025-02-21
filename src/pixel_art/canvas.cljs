(ns pixel-art.canvas ;; todo: разделить
  (:require
   [pixel-art.model.sprite :as sprite]
   [pixel-art.utils.geometry :as geometry]
   [sc.api]
   [pixel-art.model.color :as color]))

(defn create-canvas [{:keys [width height]}]
  (let [canvas (.. js/document (createElement "canvas"))]
    (set! (. canvas -width) width)
    (set! (. canvas -height) height)
    canvas))

(defn generate-data-url [draw size]
  (let [canvas (create-canvas size)]
    (draw canvas)
    (. canvas (toDataURL "image/png"))))

;; set-image-data
(defn cel-pixels->image-data [image-data {:keys [pixels size]} skip-transparent-color]
  (js/ImageData. (js/Uint8ClampedArray. (. pixels -buffer))
                 (:width size)
                 (:height size)))

;; todo: не должны лежать здесь с остальными утилитами. set-image-data
(defn draw-cel
  ([cel canvas] (draw-cel cel canvas true))
  ([cel canvas skip-transparent-color]
   (let [ctx (. canvas (getContext "2d"))
         prev-image-data (. ctx (getImageData 0 0 (:width (:size cel)) (:height (:size cel))))
         image-data (cel-pixels->image-data prev-image-data cel skip-transparent-color)]
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
        (draw-cel cel canvas true))))
  canvas)

;; todo: refactore above
(defn draw-cels-on-single-canvas [cels canvas]
  (doseq [cel (reverse cels)]
    (draw-cel cel canvas true))
  canvas)

(defn clear-canvas [canvas]
  (let [ctx (. canvas (getContext "2d"))]
    (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))))

(defn clear-canvases [canvases]
  (doseq [canvas canvases]
    (clear-canvas canvas)))

(defn to-data-url [canvas format]
  (. canvas (toDataURL (str "image/" format))))

(defn to-base64 [canvas format]
  (let [data (. canvas (toDataURL (str "image/" format)))]
    (. data (substr (+ (. data (indexOf ",")) 1)))))

(defn combine [canvas-size res-canvas-size columns canvases]
  (let [canvas-rows (partition-all columns canvases)
        res-canvas (create-canvas res-canvas-size)
        spritesheet-canvas-ctx (. res-canvas (getContext "2d"))]
    (doseq [[row-idx row] (map-indexed vector canvas-rows)
            [column-idx canvas] (map-indexed vector row)]
      (. spritesheet-canvas-ctx (drawImage canvas
                                           0 0
                                           (:width canvas-size) (:height canvas-size)
                                           (* column-idx (:width canvas-size))
                                           (* row-idx (:height canvas-size))
                                           (:width canvas-size)
                                           (:height canvas-size))))
    res-canvas))

(defn scale [size new-size canvas]
  (if (= size new-size)
    canvas
    (let [new-canvas (create-canvas new-size)
          new-canvas-ctx (. new-canvas (getContext "2d"))]
      (set! (. new-canvas-ctx -imageSmoothingEnabled) false)
      (. new-canvas-ctx (drawImage canvas 0 0 (:width size) (:height size) 0 0 (:width new-size) (:height new-size)))
      new-canvas)))

(defn ->blob-promise [canvas]
  (js/Promise. (fn [resolve] (. canvas (toBlob resolve)))))

;; todo: scale fn above?
(defn resize [target-size canvas]
  (let [target-canvas (create-canvas target-size)
        target-canvas-ctx (. target-canvas (getContext "2d"))]
    (. target-canvas-ctx save)
    (set! (. target-canvas-ctx -imageSmoothingEnabled) false)
    (. target-canvas-ctx (translate (/ (. target-canvas -width) 2)
                                    (/ (. target-canvas -height) 2)))
    (. target-canvas-ctx (scale (/ (:width target-size) (. canvas -width))
                                (/ (:height target-size) (. canvas -height))))
    (. target-canvas-ctx (drawImage canvas
                                    (/ (- (. canvas -width)) 2)
                                    (/ (- (. canvas -height)) 2)))
    (. target-canvas-ctx restore)
    target-canvas))

(defn canvas->pixels [canvas size]
  (let [image-data (.. canvas (getContext "2d") (getImageData 0 0 (:width size) (:height size)))]
    (vec (js/Uint32Array. (. (.. image-data -data) -buffer)))))

;; todo: rename
(defn set-image-data [image-data changes size]
  (let [pixels-u32arr (js/Uint32Array. (.. image-data -data -buffer))]
    (doseq [[pos color] changes
            :let [idx (geometry/pos->idx (:x pos) (:y pos) (:width size))]]
      (aset pixels-u32arr idx color))
    (js/ImageData. (js/Uint8ClampedArray. (. pixels-u32arr -buffer))
                   (:width size)
                   (:height size))))

(defn update-image-data [canvas changes]
  (let [size {:width (.-width canvas) :height (.-height canvas)}
        ctx (. canvas (getContext "2d"))]
    (-> (. ctx (getImageData 0 0 (:width size) (:height size)))
        (set-image-data changes size)
        (#(. ctx (putImageData % 0 0))))))
