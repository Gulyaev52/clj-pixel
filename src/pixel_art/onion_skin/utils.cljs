(ns pixel-art.onion-skin.utils
  (:require
   [pixel-art.model.sprite :as sprite]))

(defn get-onion-skin-frames-idx [sprite frames-count]
  (let [{:keys [frames]} sprite
        current-frame-idx (sprite/get-current-frame-idx sprite)
        prev-idxs (range (max (- current-frame-idx (:prev frames-count)) 0) current-frame-idx)
        next-idxs (range (inc current-frame-idx)
                         (inc (min (+ current-frame-idx (:next frames-count))
                                   (dec (count frames)))))]
    (set (concat prev-idxs next-idxs))))
