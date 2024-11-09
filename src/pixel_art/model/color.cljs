(ns pixel-art.model.color
  (:refer-clojure :exclude [int])
  (:require ["tinycolor2" :as tinycolor]
            ["../color.js" :as color-js]))

(defn ->int [color]
  (color-js/colorToInt color))

(defn int->rgb-str [color]
  (color-js/intToColor color))

(defn ->tinycolor [color]
  (tinycolor (int->rgb-str color)))

(defn int
  ([color]
   (->int color))
  ([r g b]
   (color-js/rgbaToInt r g b 1))
  ([r g b a]
   (color-js/rgbaToInt r g b a)))

(def transparent-color-int (int 0 0 0 0))
