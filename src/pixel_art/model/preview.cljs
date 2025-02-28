(ns pixel-art.model.preview
  (:require
   [pixel-art.utils.geometry :as geometry]))

(defn create
  ([size]
   (let [res ^js (js/Uint32Array. (* (:width size) (:height size)))]
     (set! (. res -spriteWidth) (:width size))
     (set! (. res -spriteHeight) (:height size))
     res))
  ([size pixels]
   (let [res ^js (js/Uint32Array. pixels)]
     (set! (. res -spriteWidth) (:width size))
     (set! (. res -spriteHeight) (:height size))
     res)))

(defn set-color! [^js preview x y color]
  (aset preview (geometry/pos->idx x y (. preview -spriteWidth)) color))

(defn clear [preview]
  (js/Uint32Array. (.-length preview)))
