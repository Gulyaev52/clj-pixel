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

(defn get-highlight-color [color]
  (let [dark-color (int 0 0 0 0.3)
        light-color (int 255 255 255 0.2)]
    (if (= color transparent-color-int)
      dark-color
      (let [luminance (.. (->tinycolor color) toHsl -l)]
        (if (> luminance 0.5)
          dark-color
          light-color)))))
