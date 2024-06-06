(ns pixel-art.model.sprite
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.model.layer :as layer]
            [pixel-art.utils.coll :as coll]
            [sc.api]
            [pixel-art.model.frame :as frame]))

(defn- get-layer-name [type layers-count]
  (str (if (= type :group) "Group " "Layer ") (inc layers-count)))

(def initial-frame-duration 100)

(defn create [{:keys [size]}]
  {:size size
   :current-frame-idx 0 ;; todo: может лучше исп id?
   :current-layer-idx 0
   :frames [(frame/create initial-frame-duration)]
   :cels [[(cel/create size)]] ;; array = frame; array elem = layer
   :layers [(layer/create (get-layer-name :single 0) nil)]})

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
(defn add-layer [type sprite]
  (let [{:keys [layers current-layer-idx size]} sprite
        name (get-layer-name type (count layers))
        children (if (= type :group) [] nil)
        new-current-layer-idx (inc current-layer-idx)
        new-layer (layer/create name children)
        new-cel (cel/create size)]
    (-> sprite
        (update :layers #(coll/insertv new-current-layer-idx new-layer %))
        (assoc :current-layer-idx new-current-layer-idx)
        (update :cels (fn [cels] (map #(coll/insertv new-current-layer-idx new-cel %) cels))))))

(defn remove-layer [idx sprite])

(defn duplicate-layer [])

(defn move-layer [from-idx to-idx sprite])

(defn move-layer-up [idx sprite]
  (move-layer idx (inc idx) sprite))

(defn move-layer-down [idx sprite]
  (move-layer idx (dec idx) sprite))

(defn merge-layer-with-up [idx sprite])

(defn merge-layer-with-down [idx sprite])

(defn select-layer [idx sprite]
  (assoc sprite :current-layer-idx idx))

(defn add-frame [sprite]
  (let [{:keys [current-frame-idx size frames]} sprite
        new-current-frame-idx (inc current-frame-idx)
        new-frame (frame/create initial-frame-duration)
        new-cels (vec (repeat (count frames) (cel/create size)))]
    (-> sprite
        (update :frames #(coll/insertv new-current-frame-idx new-frame %))
        (assoc :current-frame-idx new-current-frame-idx)
        (update :cels #(coll/insertv new-current-frame-idx new-cels %)))))

;; todo: cannot be empty
(defn remove-frame [idx sprite])

(defn duplicate-frame [idx sprite])

(defn move-frame [from to sprite]
  (-> sprite
      (update :frames #(coll/swapv from to %))
      (assoc :current-frame-idx to)))

(defn select-frame [idx sprite]
  (assoc sprite :current-frame-idx idx))

(defn get-current-frame [{:keys [current-frame-idx frames]}]
  (nth frames current-frame-idx))
