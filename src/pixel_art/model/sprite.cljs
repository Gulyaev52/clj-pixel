(ns pixel-art.model.sprite
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.utils.coll :as coll]
            [sc.api]))

(defn create [{:keys [size layer frame cel]}]
  {:size size
   :current-frame-idx 0 ;; todo: может лучше исп id?
   :current-layer-idx 0
   :frames [frame]
   :cels [[cel]] ;; array = frame; array elem = layer
   :layers [layer]})

(defn resize [sprite])

(defn get-frame-cels [frame-idx sprite]
  (nth (:cels sprite) frame-idx))

(defn get-size [sprite] (:size sprite))

(defn get-current-cel [{:keys [current-frame-idx current-layer-idx cels]}]
  (get-in cels [current-frame-idx current-layer-idx]))

(defn update-current-cel [f {:keys [current-frame-idx current-layer-idx] :as sprite}]
  (update-in sprite [:cels current-frame-idx current-layer-idx] f))

;; type: group | single
;; type: implement for group
;; logic depends on selected-layer
(defn add-layer
  ([layer sprite]
   (add-layer layer (fn [] (cel/create (:size sprite))) sprite))
  ([layer create-new-cel sprite]
   (let [{:keys [current-layer-idx]} sprite
         new-current-layer-idx (inc current-layer-idx)]
     (-> sprite
         (update :layers #(coll/insertv new-current-layer-idx layer %))
         (assoc :current-layer-idx new-current-layer-idx)
         (update :cels (fn [cels] (map-indexed (fn [frame-idx cels]
                                                 (coll/insertv new-current-layer-idx
                                                               (create-new-cel frame-idx)
                                                               cels))
                                               cels)))))))

(defn remove-layer [idx sprite]
  (-> sprite
      (update :layers #(coll/removev idx %))
      (update :cels (fn [cels] (map #(coll/removev idx %) cels)))
      (#(if (= idx (:current-layer-idx sprite))
          (assoc % :current-layer-idx (min idx (dec (count (:layers %)))))
          sprite))))

(defn duplicate-layer [sprite]
  (let [{:keys [current-layer-idx layers cels]} sprite
        current-layer (nth layers current-layer-idx)]
    (add-layer current-layer
               (fn [frame-idx] (get-in [frame-idx current-layer-idx] cels))
               sprite)))

(defn move-layer [from-idx to-idx sprite]
  (-> sprite
      (update :layers #(coll/swapv from-idx to-idx %))
      (update :cels (fn [cels] (map #(coll/swapv from-idx to-idx %) cels)))
      (assoc :current-layer-idx to-idx)))

(defn move-layer-up [idx sprite]
  (move-layer idx (inc idx) sprite))

(defn move-layer-down [idx sprite]
  (move-layer idx (dec idx) sprite))

(defn merge-layer-with-below [sprite]
  (if (and (< (:current-layer-idx sprite) (count (:layers sprite)))
           (> (count (:layers sprite)) 1))
    (let [{:keys [current-layer-idx]} sprite
          below-layer-idx (inc current-layer-idx)]
      (-> sprite
          (update :cels #(for [cel-row %]
                           (cel/merge-cels (nth cel-row below-layer-idx)
                                           (nth cel-row current-layer-idx))))
          (#(remove-layer below-layer-idx %))))
    sprite))

(defn select-layer [idx sprite]
  (assoc sprite :current-layer-idx idx))

(defn add-frame
  ([frame sprite]
   (let [new-cels (vec (repeat (count (:frames sprite)) (cel/create (:size sprite))))]
     (add-frame frame new-cels sprite)))
  ([frame new-cels sprite]
   (let [{:keys [current-frame-idx]} sprite
         new-current-frame-idx (inc current-frame-idx)]
     (-> sprite
         (update :frames #(coll/insertv new-current-frame-idx frame %))
         (assoc :current-frame-idx new-current-frame-idx)
         (update :cels #(coll/insertv new-current-frame-idx new-cels %))))))

;; todo: cannot be empty
(defn remove-frame [sprite]
  (let [{:keys [current-frame-idx]} sprite]
    (-> sprite
        (update :frames #(coll/removev current-frame-idx %))
        (update :cels #(coll/removev current-frame-idx %))
        (#(assoc % :current-frame-idx (min current-frame-idx (dec (count (:frames %)))))))))

(defn duplicate-frame [sprite]
  (let [current-frame (nth (:frames sprite) (:current-frame-idx sprite))
        current-frame-cels (get-frame-cels (:current-frame-idx sprite) sprite)]
    (add-frame current-frame current-frame-cels sprite)))

(defn move-frame [from to sprite]
  (-> sprite
      (update :frames #(coll/swapv from to %))
      (update :cels #(coll/swapv from to %))
      (assoc :current-frame-idx to)))

(defn select-frame [idx sprite]
  (assoc sprite :current-frame-idx idx))

(defn get-current-frame [{:keys [current-frame-idx frames]}]
  (nth frames current-frame-idx))

(defn remove-cel [sprite]
  (update-current-cel cel/remove-all-pixels sprite))

(defn move-cel [])
