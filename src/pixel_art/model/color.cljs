(ns pixel-art.model.color)

(defn rgba
  ([r g b]
   (rgba r g b 1))
  ([r g b a]
   (str "rgba(" r ", " g ", " b ", " a ")")))

(def transparent-color (rgba 0 0 0 0))
