(ns pixel-art.model.sprite
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.utils.coll :as coll]
            [sc.api]))

(defn create [{:keys [size layer frame cel]}]
  {:size size
   :current-frame-idx 0 ;; todo: может лучше исп id?
   :current-layer-idx 0
   :selected-cels-pos #{}
   :frames [frame]
   :cels [[cel]] ;; array = frame; array elem = layer
   :layers [layer]
   :linked-cel-groups {}}) ;; когда происходит удаление ячейки/перенос, тогда же нужно удалять и отсюда. удаление фрейма, удаление слоя

(defn resize [sprite])

(defn get-frame-cels [frame-idx sprite]
  (nth (:cels sprite) frame-idx))

(defn get-frame-cels-with-layers [frame-idx sprite]
  (let [{:keys [layers]} sprite]
    (->> (get-frame-cels frame-idx sprite)
         (map-indexed (fn [idx cel]
                        (assoc cel :layer (nth layers idx)))))))

(defn get-size [sprite] (:size sprite))

(defn get-cel [{:keys [frame-idx layer-idx]} sprite]
  (-> (:cels sprite) (nth frame-idx) (nth layer-idx)))

(defn get-current-cel [sprite]
  (get-cel {:frame-idx (:current-frame-idx sprite) :layer-idx (:current-layer-idx sprite)}
           sprite))

;; todo: rename
(defn- update-cel [f pos sprite]
  (update-in sprite [:cels (:frame-idx pos) (:layer-idx pos)] f))

(defn- update-cels [f cels-pos sprite]
  (reduce (fn [res-sprite cel-pos] (update-cel f cel-pos res-sprite))
          sprite
          cels-pos))

(defn- get-linked-cels-pos [cel-pos linked-cel-groups]
  (let [group-number (-> linked-cel-groups (get cel-pos) :group-number)]
    (->> linked-cel-groups
         vals
         (filter #(= (:group-number %) group-number)))))

(defn- update-current-cel-and-linked [f {:keys [current-frame-idx current-layer-idx cels linked-cel-groups] :as sprite}]
  (let [updated-current-cel (f (get-in cels [current-frame-idx current-layer-idx]))
        current-cel-pos {:frame-idx current-frame-idx
                         :layer-idx current-layer-idx}
        linked-cels-pos (get-linked-cels-pos current-cel-pos linked-cel-groups)]
    (update-cels (fn [] updated-current-cel) (set (conj linked-cels-pos current-cel-pos)) sprite)))

(defn set-current-cel-pixels [pixels-map sprite]
  (update-current-cel-and-linked #(cel/set-pixels pixels-map %) sprite))

(defn set-current-cel-opacity [opacity sprite]
  (update-current-cel-and-linked #(assoc % :opacity opacity) sprite))

;; todo: array of selected cels
;; todo: нужно так же удалять/добавлять в selected-cels-pos и в linked-cel-groups
;; todo: логика добавление и удаление в selected-cels-pos
;; todo: обновление связ ячеек при измен одной из них
;; todo: нужно ли сбрасывать selected-cels после действия(link, unlink и тд)
;; todo: automatic-linking

;; если у главного нет группы, то создаётся новая
;; иначе добавляется в уже сущ
;; если в selected-cels-pos с разных слоёв то хендлим только те же что и с main-cel-pos на одном слое
;; нужно главную скопировать во все остальные места
;; если ничего не выбрано то и не надо?
(defn link-selected-cels [main-cel-pos sprite]
  (let [{:keys [selected-cels-pos linked-cel-groups]} sprite
        group-number (or
                      (get linked-cel-groups main-cel-pos)
                      (inc (apply max (map :group-number linked-cel-groups))))
        new-linked-cel-groups (->> selected-cels-pos
                                   (filter #(= (:layer-idx %) (:layer-idx main-cel-pos)))
                                   (map (fn [pos] [pos (assoc pos :group-number group-number)]))
                                   (#(merge linked-cel-groups %)))
        main-cel (get-cel main-cel-pos sprite)]
    (-> sprite
        (assoc :linked-cel-groups new-linked-cel-groups)
        (#(update-cels (fn [] main-cel) selected-cels-pos %))))) ;; todo: update-current-cels and linked?

(defn unlink-selected-cels [sprite]
  (let [{:keys [selected-cels-pos]} sprite]
    (update sprite :linked-cel-groups #(apply dissoc % selected-cels-pos))))

(defn select-cel [{:keys [frame-idx layer-idx]} sprite]
  (assoc sprite
         :current-frame-idx frame-idx
         :current-layer-idx layer-idx))

(defn clear-cel [sprite]
  (update-current-cel-and-linked cel/remove-all-pixels sprite))

(defn move-cel []) ;; todo: linked?

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
         (update :cels (fn [cels] (->> (map-indexed (fn [frame-idx cels]
                                                      (coll/insertv new-current-layer-idx
                                                                    (create-new-cel frame-idx)
                                                                    cels))
                                                    cels)
                                       vec)))))))

(defn remove-layer [idx sprite]
  (let [new-layers (coll/removev idx (:layers sprite))
        new-cels (map #(coll/removev idx %) (:cels sprite))
        removed-cels-pos (->> (range 0 (count (:frames sprite)))
                              (map (fn [frame-idx] {:frame-idx frame-idx :layer-idx idx})))
        new-linked-cel-groups (apply (partial dissoc (:linked-cel-groups sprite)) removed-cels-pos)]
    (-> sprite
        (assoc :layers new-layers)
        (assoc :cels new-cels)
        (assoc :linked-cel-groups new-linked-cel-groups)
        (#(if (= idx (:current-layer-idx sprite))
            (assoc % :current-layer-idx (min idx (dec (count (:layers %)))))
            %)))))

(defn duplicate-layer [sprite]
  (let [{:keys [current-layer-idx layers cels]} sprite
        current-layer (nth layers current-layer-idx)]
    (add-layer current-layer
               (fn [frame-idx] (get-in [frame-idx current-layer-idx] cels))
               sprite)))

;; todo: обновить linked-cel-groups
(defn- move-layer [from-idx to-idx sprite]
  (-> sprite
      (update :layers #(coll/swapv from-idx to-idx %))
      (update :cels (fn [cels] (mapv #(coll/swapv from-idx to-idx %) cels)))
      (assoc :current-layer-idx to-idx)))

(defn move-layer-up [idx sprite]
  (move-layer idx (dec idx) sprite))

(defn move-layer-down [idx sprite]
  (move-layer idx (inc idx) sprite))

;; todo: linked cels 
(defn merge-layer-with-below [sprite]
  (if (and (< (:current-layer-idx sprite) (count (:layers sprite)))
           (> (count (:layers sprite)) 1))
    (let [{:keys [current-layer-idx]} sprite
          below-layer-idx (inc current-layer-idx)]
      (-> sprite
          (update :cels
                  (fn [cels]
                    (map (fn [cel-row]
                           (update cel-row
                                   current-layer-idx
                                   #(cel/merge-cels (nth cel-row below-layer-idx) %)))
                         cels)))
          (#(remove-layer below-layer-idx %))))
    sprite))

(defn update-layer [idx f sprite]
  (update-in sprite [:layers idx] f))

(defn select-layer [idx sprite]
  (assoc sprite :current-layer-idx idx))

(defn get-current-cel-pos [sprite]
  {:frame-idx (:current-frame-idx sprite)
   :layer-idx (:current-layer-idx sprite)})

(defn get-current-layer [sprite]
  (-> sprite :layers (nth (:current-layer-idx sprite))))

(defn add-frame
  ([frame sprite]
   (let [new-cels (vec (repeat (count (:layers sprite)) (cel/create (:size sprite))))]
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
  (let [{:keys [current-frame-idx layers linked-cel-groups]} sprite
        removed-cels-pos (->> (range 0 (count layers))
                              (map (fn [layer-idx] {:frame-idx current-frame-idx
                                                    :layer-idx layer-idx})))
        new-linked-cel-groups (apply (partial dissoc linked-cel-groups) removed-cels-pos)]
    (-> sprite
        (update :frames #(coll/removev current-frame-idx %))
        (update :cels #(coll/removev current-frame-idx %))
        (assoc :linked-cel-groups new-linked-cel-groups)
        (#(assoc % :current-frame-idx (min current-frame-idx (dec (count (:frames %)))))))))

(defn duplicate-frame [sprite]
  (let [current-frame (nth (:frames sprite) (:current-frame-idx sprite))
        current-frame-cels (get-frame-cels (:current-frame-idx sprite) sprite)]
    (add-frame current-frame current-frame-cels sprite)))

;; todo: обновить linked
(defn move-frame [from to sprite]
  (-> sprite
      (update :frames #(coll/swapv from to %))
      (update :cels #(coll/swapv from to %))
      (assoc :current-frame-idx to)))

(defn select-frame [idx sprite]
  (assoc sprite :current-frame-idx idx))

(defn get-current-frame [{:keys [current-frame-idx frames]}]
  (nth frames current-frame-idx))
