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

(defn draw-cel [cel canvas]
  (let [ctx (. canvas (getContext "2d"))
        size (cel/get-size cel)]
    (doseq [x (range 0 (:width size))
            y (range 0 (:height size))]
      (when-let [color (cel/get-pixel {:x x :y y} cel)]
        (set! (. ctx -fillStyle) (.. (tinycolor color)
                                     (setAlpha (:opacity cel))
                                     (toRgbString)))
        (. ctx (fillRect x y 1 1))))
    canvas))

(defn draw-frame [frame-idx sprite]
  (let [{:keys [layer-idx]} (sprite/get-current-cel-pos sprite)
        cels (sprite/get-frame-cels-with-layers frame-idx sprite)
        cels-above [(reverse (take layer-idx cels)) (. js/document (getElementById "layers-above"))]
        cels-below [(reverse (drop (inc layer-idx) cels)) (. js/document (getElementById "layers-below"))]
        current-cel [[(nth cels layer-idx)] (. js/document (getElementById "current-layer"))]]
    (doseq [[cels canvas] [cels-above cels-below current-cel]]
      (doseq [cel cels]
        (when (-> cel :layer :visibile?)
          (draw-cel cel canvas))))))

(defn draw-frame-on-single-canvas [frame-idx sprite canvas]
  (let [cels (sprite/get-frame-cels-with-layers frame-idx sprite)]
    (doseq [cel (reverse cels)]
      (when (-> cel :layer :visibile?)
        (draw-cel cel canvas)))))

;; todo: refactore above
(defn draw-cels-on-single-canvas [cels canvas]
  (doseq [cel (reverse cels)]
    (draw-cel cel canvas))
  canvas)

(defn clear-canvas [canvas]
  (let [ctx (. canvas (getContext "2d"))]
    (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))))

(defn clear-canvases [canvases]
  (doseq [canvas canvases]
    (clear-canvas canvas)))

(defn get-canvas-context [id]
  (let [canvas (. js/document (getElementById id))]
    (.. canvas (getContext "2d"))))

(defn to-data-url [canvas format]
  (. canvas (toDataURL (str "image/" format))))

(defn to-base64 [canvas format]
  (let [data (. canvas (toDataURL (str "image/" format)))]
    (. data (substr (+ (. data (indexOf ",")) 1)))))

(defn create-canvas [{:keys [width height]}]
  (let [canvas (.. js/document (createElement "canvas"))]
    (set! (. canvas -width) width)
    (set! (. canvas -height) height)
    canvas))
