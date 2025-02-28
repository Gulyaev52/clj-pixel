(ns pixel-art.model.cel
  (:require
   [pixel-art.model.color :as color]
   [pixel-art.utils.geometry :as geometry]))

(extend-type js/Uint32Array
  ICounted
  (-count [this] (.-length this))
  IIndexed
  (-nth
    ([coll n]
     (aget coll n))
    ([coll n not-found]
     (or (aget coll n) not-found))))

(defn create-pixels-coll [size]
  (let [arr (js/Uint32Array. (* (:width size) (:height size)))]
    (. arr (set (clj->js (repeat (* (:width size) (:height size)) color/transparent-color-int))))
    arr))

(defn pos->idx [pos width]
  (+ (:x pos) (* width (:y pos))))

(defn idx->pos [idx {:keys [width]}]
  {:x (rem idx width)
   :y (. js/Math (floor (/ idx width)))})

(defn update-pixels-coll [pixels-map size pixels]
  (let [{:keys [width]} size
        copy (. pixels (slice))]
    (doseq [[pos color] pixels-map]
      (when (geometry/valid-point? pos size)
        (aset copy (pos->idx pos width) color)))
    copy))

(defn create
  ([size]
   (create size (create-pixels-coll size)))
  ([size pixels]
   {:pixels pixels ;; todo: везде работает с pixels-map а тут нет
    :size size
    :current false
    :selected false}))

(defn remove-all-pixels [cel]
  (update cel :pixels #(mapv (fn [_] color/transparent-color-int) %)))

(defn set-pixels [pixels-map cel]
  (update cel :pixels #(update-pixels-coll pixels-map (:size cel) %)))

(defn set-pixels2 [pixels cel]
  (assoc cel :pixels pixels))

(defn merge-cels [below-cel above-cel]
  (let [above-cel-pixels-map (->> (:pixels above-cel)
                                  (map-indexed (fn [idx color]
                                                 [(idx->pos idx (:size above-cel)) color]))
                                  (remove #(= (second %) color/transparent-color-int)))]
    (assoc above-cel
           :pixels
           (update-pixels-coll above-cel-pixels-map
                               (:size above-cel)
                               (:pixels below-cel)))))
(comment
  (def above-cel (->> (create {:width 2 :height 2})
                      (set-pixels {{:x 0 :y 0} (color/int 0 0 0 1)
                                   {:x 1 :y 1} (color/int 0 0 0 1)})))
  (def below-cel (->> (create {:width 2 :height 2})
                      (set-pixels {{:x 0 :y 0} (color/int 255 0 0)
                                   {:x 0 :y 1} (color/int 0 255 0)})))
  (merge-cels below-cel above-cel))

(def get-size :size)

(defn get-pixel [pos cel]
  (let [{:keys [pixels size]} cel]
    (nth pixels (pos->idx pos (:width size)) color/transparent-color-int)))

(defn pixels->coll [cel]
  (map-indexed (fn [idx pixel] [(idx->pos idx (:size cel)) pixel])
               (:pixels cel)))

(defn map-pixels [f cel]
  (map-indexed (fn [idx pixel] (f (idx->pos idx (:size cel)) pixel))
               (:pixels cel)))
