(ns pixel-art.model.cel
  (:require [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.utils.geometry :as geometry]))

(defn create [size]
  {:pixels (vec (repeat (* (:width size) (:height size)) transparent-color))
   :size size
   :opacity 1
   :current false
   :selected false})

(defn remove-all-pixels [cel]
  (update cel :pixels #(mapv (fn [_] transparent-color) %)))

(defn pos->idx [{:keys [x y]} {:keys [width]}]
  (+ x (* width y)))

(defn idx->pos [idx {:keys [width]}]
  {:x (rem idx width)
   :y (. js/Math (floor (/ idx width)))})

(defn resize [size cel])

(defn- update-pixels-coll [pixels-map size pixels]
  (reduce (fn [res-pixels [pos color]]
            (if (geometry/valid-point? pos size)
              (assoc res-pixels (pos->idx pos size) color)
              res-pixels))
          pixels
          pixels-map))

(defn set-pixels [pixels-map cel]
  (update cel :pixels #(update-pixels-coll pixels-map (:size cel) %)))

(defn merge-cels [below-cel above-cel]
  (let [above-cel-pixels-map (->> (:pixels above-cel)
                                  (map-indexed (fn [idx color]
                                                 [(idx->pos idx (:size above-cel)) color]))
                                  (remove #(= (second %) transparent-color)))]
    (assoc above-cel
           :pixels
           (update-pixels-coll above-cel-pixels-map
                               (:size above-cel)
                               (:pixels below-cel)))))
(comment
  (def above-cel (->> (create {:width 2 :height 2})
                      (set-pixels {{:x 0 :y 0} "black"
                                   {:x 1 :y 1} "black"})))
  (def below-cel (->> (create {:width 2 :height 2})
                      (set-pixels {{:x 0 :y 0} "red"
                                   {:x 0 :y 1} "green"})))
  (merge-cels below-cel above-cel))

(defn emptyy? [cel]
  (every? #(= % transparent-color) (:pixels cel)))

(def get-size :size)

(defn get-pixel [pos cel]
  (let [{:keys [pixels size]} cel]
    (nth pixels (pos->idx pos size) transparent-color)))

(defn pixels->coll [cel]
  (map-indexed (fn [idx pixel] [(idx->pos idx (:size cel)) pixel])
               (:pixels cel)))
