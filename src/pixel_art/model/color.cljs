(ns pixel-art.model.color
  (:require ["tinycolor2" :as tinycolor]))

(defn rgba
  ([color]
   (let [rgba-obj (. (tinycolor color) toRgb)]
     (rgba (. rgba-obj -r) (. rgba-obj -g) (. rgba-obj -b) (. rgba-obj -a))))
  ([r g b]
   (rgba r g b 1))
  ([r g b a]
   (str "rgba(" r ", " g ", " b ", " a ")")))

(def transparent-color (rgba 0 0 0 0))

(defn ->tinycolor [color]
  (tinycolor color))
