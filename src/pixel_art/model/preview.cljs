(ns pixel-art.model.preview
  (:require
   [pixel-art.utils.geometry :as geometry]))

(defn create
  ([size]
   {:pixels (js/Uint32Array. (* (:width size) (:height size)))
    :size size})
  ([size pixels]
   {:pixels (js/Uint32Array. pixels)
    :size size}))

(defn set-color! [preview x y color]
  (aset (:pixels preview) (geometry/pos->idx x y (:width (:size preview))) color))

(defn clear [preview]
  (js/Uint32Array. (.-length preview)))
