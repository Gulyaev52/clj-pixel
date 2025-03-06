(ns pixel-art.model.preview
  (:require
   [pixel-art.utils.geometry :as geometry]))

(defn create
  ([size]
   (let [res ^js (js/Uint32Array. (* (:width size) (:height size)))
         width (:width size)
         height (:height size)]
     (set! (. res -spriteSize) #js [(:width size) (:height size)])
     ;; todo: comment this
     (set! (. res -getColor) (fn [^js preview x y]
                               (aget preview (geometry/pos->idx x y width height))))
     (set! (. res -setColor) (fn [^js preview x y color]
                               (aset preview (geometry/pos->idx x y width height) color)))
     (set! (. res -setColorByIdx)
           (fn [^js preview idx color]
             (aset preview idx color)))
     res))
  ([size pixels]
   (let [res ^js (js/Uint32Array. pixels)
         width (:width size)
         height (:height size)]
     (set! (. res -spriteSize) #js [(:width size) (:height size)])
     (set! (. res -getColor) (fn [^js preview x y]
                               (aget preview (geometry/pos->idx x y width height))))
     (set! (. res -setColor) (fn [^js preview x y color]
                               (aset preview (geometry/pos->idx x y width height) color)))
     (set! (. res -setColorByIdx)
           (fn [^js preview idx color]
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

(defn ->vec [preview]
  (vec preview))
