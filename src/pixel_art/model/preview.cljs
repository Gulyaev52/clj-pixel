(ns pixel-art.model.preview)

(defn create [size]
  (js/Uint32Array. (* (:width size) (:height size))))

(defn clear [preview]
  (js/Uint32Array. (.-length preview)))
