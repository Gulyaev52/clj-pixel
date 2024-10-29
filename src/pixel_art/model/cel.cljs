(ns pixel-art.model.cel
  (:require [pixel-art.utils.geometry :as geometry]
            [pixel-art.model.color :as color]))

(defn create-pixels-coll [size]
  (vec (repeat (* (:width size) (:height size)) color/transparent-color)))

(defn pos->idx [{:keys [x y]} {:keys [width]}]
  (+ x (* width y)))

(defn idx->pos [idx {:keys [width]}]
  {:x (rem idx width)
   :y (. js/Math (floor (/ idx width)))})

(defn update-pixels-coll [pixels-map size pixels]
  (let [pixels-t (transient pixels)] ;; todo: тут возможно нужно юзать reduce
    (doseq [[pos color] pixels-map]
      (when (geometry/valid-point? pos size)
        (assoc! pixels-t (pos->idx pos size) color)))
    (persistent! pixels-t)))

(defn create
  ([size]
   (create size (create-pixels-coll size)))
  ([size pixels]
   {:pixels pixels ;; todo: везде работает с pixels-map а тут нет
    :size size
    :current false
    :selected false}))

(defn remove-all-pixels [cel]
  (update cel :pixels #(mapv (fn [_] color/transparent-color) %)))

(defn set-pixels [pixels-map cel]
  (update cel :pixels #(update-pixels-coll pixels-map (:size cel) %)))

(defn merge-cels [below-cel above-cel]
  (let [above-cel-pixels-map (->> (:pixels above-cel)
                                  (map-indexed (fn [idx color]
                                                 [(idx->pos idx (:size above-cel)) color]))
                                  (remove #(= (second %) color/transparent-color)))]
    (assoc above-cel
           :pixels
           (update-pixels-coll above-cel-pixels-map
                               (:size above-cel)
                               (:pixels below-cel)))))
(comment
  (def above-cel (->> (create {:width 2 :height 2})
                      (set-pixels {{:x 0 :y 0} (color/rgba 0 0 0 1)
                                   {:x 1 :y 1} (color/rgba 0 0 0 1)})))
  (def below-cel (->> (create {:width 2 :height 2})
                      (set-pixels {{:x 0 :y 0} (color/rgba 255 0 0)
                                   {:x 0 :y 1} (color/rgba 0 255 0)})))
  (merge-cels below-cel above-cel))

(defn emptyy? [cel]
  (every? #(= % color/transparent-color) (:pixels cel)))

(def get-size :size)

(defn get-pixel [pos cel]
  (let [{:keys [pixels size]} cel]
    (nth pixels (pos->idx pos size) color/transparent-color)))

(defn pixels->coll [cel]
  (map-indexed (fn [idx pixel] [(idx->pos idx (:size cel)) pixel])
               (:pixels cel)))
