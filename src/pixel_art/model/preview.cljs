(ns pixel-art.model.preview
  (:require
   [pixel-art.utils.geometry :as geometry]))

(defn create
  ([size]
   (create size (js/Uint32Array. (* (:width size) (:height size)))))
  ([size ^js preview-or-pixels]
   (let [res (js/Uint32Array. preview-or-pixels)
         width (:width size)
         height (:height size)]
     (set! (. res -changed) (or (.-changed preview-or-pixels) false))
     (set! (. res -spriteSize) #js [(:width size) (:height size)])
     (set! (. res -getColor) (fn [^js preview x y]
                               (aget preview (geometry/pos->idx x y width height))))
     (set! (. res -setColor) (fn [^js preview x y color]
                               (when-let [idx (geometry/pos->idx x y width height)]
                                 (when (and (not (. preview -changed)) (not= (aget preview idx) color))
                                   (set! (. preview -changed) true))
                                 (aset preview idx color))))
     (set! (. res -setColorByIdx)
           (fn [^js preview idx color]
             (when (and (not (. preview -changed)) (not= (aget preview idx) color))
               (set! (. preview -changed) true))
             (aset preview idx color)))
     res)))

(defn get-color [^js preview x y]
  (let [getColor (.-getColor preview)]
    (getColor preview x y)))

(defn set-color!
  ([^js preview idx color]
   (let [setColorByIdx (.-setColorByIdx preview)]
     (setColorByIdx preview idx color)))
  ([^js preview x y color]
   (let [setColor (.-setColor preview)]
     (setColor preview x y color))))

(defn clear [preview]
  (js/Uint32Array. (.-length preview)))

(defn changed? [^js preview]
  (. preview -changed))
