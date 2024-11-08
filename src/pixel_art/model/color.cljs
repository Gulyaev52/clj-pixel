(ns pixel-art.model.color
  (:require ["tinycolor2" :as tinycolor]
            ["../color.js" :as color-js]))

(defn ->tinycolor [color]
  (tinycolor color))

(defn ->int [color]
  (color-js/colorToInt color))

(defn int->color [color]
  (color-js/intToColor color))

(defn rgba
  ([color]
   (->int color))
  ([r g b]
   (color-js/rgbaToInt r g b 1))
  ([r g b a]
   (color-js/rgbaToInt r g b a)))

(def transparent-color (rgba 0 0 0 0))
