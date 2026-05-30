(ns pixel-art.utils.canvas
  (:require
   [sc.api]))

(defn create-canvas [{:keys [width height]}]
  (let [canvas (.. js/document (createElement "canvas"))]
    (set! (. canvas -width) width)
    (set! (. canvas -height) height)
    canvas))

(defn generate-data-url [draw size]
  (let [canvas (create-canvas size)]
    (draw canvas)
    (. canvas (toDataURL "image/png"))))

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
    (js/Uint32Array. (. (.. image-data -data) -buffer))))
