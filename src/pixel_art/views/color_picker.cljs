(ns pixel-art.views.color-picker
  (:require
   ["./colorPicker$default" :as color-picker-js]
   [pixel-art.model.color :as color]
   [reagent.core :as reag]))

(defn color-picker [{:keys [value preset-colors actions on-change]}]
  [:> color-picker-js
   {:color (color/int->rgb-str value)
    :disableAlpha true
    :presetColors (clj->js (map #(update % :color color/int->rgb-str) preset-colors))
    :actions (clj->js (map #(reag/as-element %) actions))
    :onChange (fn [e] (let [rgba (. e -rgba)]
                        (on-change (color/int (. rgba -r) (. rgba -g) (. rgba -b) (. rgba -a)))))}])
