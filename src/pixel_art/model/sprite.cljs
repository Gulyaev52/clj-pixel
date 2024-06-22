(ns pixel-art.model.sprite
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.utils.coll :as coll]
            [sc.api]))

(defn create [{:keys [size layer frame cel]}]
  {:size size
   :selected-cels-pos #{{:frame-idx 0 :layer-idx 0}} ;; todo: use set
   :current-cel-pos {:frame-idx 0 :layer-idx 0}
   :frames [frame]
   :cels [cel]
   :layers [layer]
   :linked-cel-groups {}})

;; todo: test merge-layer-with-below
;; todo: bulk delete layers, bulk delete frames, buld remove cels and etc
;; todo: current в selected-cels
;; todo: исп id?

(defn resize [sprite])

(defn get-current-cel-pos [sprite]
  (:current-cel-pos sprite))

(defn select-only-1-cel [cel-pos sprite]
  (-> sprite
      (assoc :selected-cels-pos #{cel-pos})
      (assoc :current-cel-pos cel-pos)))

;; todo: rename
(defn- select-cel [cel-pos sprite]
  (assoc sprite :current-cel-pos cel-pos))

;; todo: rename
(defn add-cel-to-selection [cel-pos sprite]
  (let [new-selected-cels-pos (conj (:selected-cels-pos sprite) cel-pos)]
    (-> sprite
        (assoc :selected-cels-pos new-selected-cels-pos)
        (assoc :current-cel-pos cel-pos))))

(defn toggle-cel-to-selection [cel-pos sprite]
  (cond
    (and (= (count (:selected-cels-pos sprite)) 1)
         (= cel-pos (:current-cel-pos sprite)))
    sprite

    ((:selected-cels-pos sprite) cel-pos)
    (let [selected-cels-pos (disj (:selected-cels-pos sprite) cel-pos)
          current-cel-pos (if (= (:current-cel-pos sprite) cel-pos)
                            (first selected-cels-pos)
                            (:current-cel-pos sprite))]
      (-> sprite
          (assoc :selected-cels-pos selected-cels-pos)
          (assoc :current-cel-pos current-cel-pos)))

    :else (add-cel-to-selection cel-pos sprite)))

(defn add-cels-range-to-selection [cel-pos sprite]
  (let [current-cel-pos (get-current-cel-pos sprite)
        [less-cel more-cel] (if (and (< (:frame-idx current-cel-pos) (:frame-idx cel-pos))
                                     (< (:layer-idx current-cel-pos) (:layer-idx cel-pos)))
                              [current-cel-pos cel-pos]
                              [cel-pos current-cel-pos])
        new-selected-cels (flatten
                           (for [frame-idx (range (:frame-idx less-cel) (inc (:frame-idx more-cel)))]
                             (for [layer-idx (range (:layer-idx less-cel) (inc (:frame-idx more-cel)))]
                               {:frame-idx frame-idx
                                :layer-idx layer-idx})))]
    (->> (reduce #(add-cel-to-selection %2 %1) sprite new-selected-cels)
         (select-cel cel-pos))))

;; todo: rename потому что не понятно что остальные дропаются?
(defn select-frame [idx sprite]
  (let [current-pos (get-current-cel-pos sprite)]
    (select-only-1-cel (assoc current-pos :frame-idx idx) sprite)))

;; todo: ?
(defn select-layer [idx sprite]
  (let [current-pos (get-current-cel-pos sprite)]
    (select-only-1-cel (assoc current-pos :layer-idx idx) sprite)))

(defn get-current-frame-idx [sprite]
  (:frame-idx (get-current-cel-pos sprite)))

(defn get-current-layer-idx [sprite]
  (:layer-idx (get-current-cel-pos sprite)))

(defn get-frame-cels [frame-idx sprite]
  (->> (:cels sprite)
       (filter #(= (-> % :pos :frame-idx) frame-idx))))

(defn get-frame-cels-with-layers [frame-idx sprite]
  (let [{:keys [layers]} sprite]
    (->> (get-frame-cels frame-idx sprite)
         (map-indexed (fn [idx cel]
                        (assoc cel :layer (nth layers idx)))))))

(defn get-size [sprite] (:size sprite))

(defn get-cel [pos sprite]
  (->> (:cels sprite)
       (coll/find-first #(= (:pos %) pos))))

(defn get-current-cel [sprite]
  (get-cel (get-current-cel-pos sprite) sprite))

(defn get-current-frame [sprite]
  (nth (:frames sprite) (get-current-frame-idx sprite)))

;; todo: rename
(defn- update-cel [f pos sprite]
  (update sprite
          :cels
          #(coll/update-byv (fn [c] (= (:pos c) pos)) f %)))

(defn- update-cels [f cels-pos sprite]
  (reduce (fn [res-sprite cel-pos] (update-cel f cel-pos res-sprite))
          sprite
          cels-pos))

(defn- get-linked-cels-pos [cel-pos linked-cel-groups]
  (let [group-number (-> linked-cel-groups (get cel-pos) :group-number)]
    (->> linked-cel-groups
         vals
         (filter #(= (:group-number %) group-number)))))

(defn- update-current-cel-and-linked [f sprite]
  (let [updated-current-cel (f (get-current-cel sprite))
        current-cel-pos (get-current-cel-pos sprite)
        linked-cels-pos (get-linked-cels-pos current-cel-pos (:linked-cel-groups sprite))]
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

(defn clear-cel [sprite]
  (update-current-cel-and-linked cel/remove-all-pixels sprite))

(defn move-cel []) ;; todo: linked?

;; type: group | single
;; type: implement for group
;; logic depends on selected-layer
(defn add-layer
  ([layer sprite]
   (add-layer layer (fn [pos] (cel/create (:size sprite) pos)) sprite))
  ([layer create-new-cel sprite]
   (let [current-layer-idx (get-current-layer-idx sprite)
         new-current-layer-idx (inc current-layer-idx)
         new-cels (map (fn [frame-idx] (create-new-cel {:frame-idx frame-idx :layer-idx new-current-layer-idx}))
                       (range 0 (count (:frames sprite))))]
     (-> sprite
         (update :layers #(coll/insertv new-current-layer-idx layer %))
         (#(select-layer new-current-layer-idx %))
         (update :cels #(into % new-cels))))))

(defn remove-layer [idx sprite]
  (let [new-layers (coll/removev idx (:layers sprite))
        new-cels (vec (remove #(= (-> % :pos :layer-idx) idx) (:cels sprite)))
        current-cel-pos (get-current-cel-pos sprite)
        removed-cels-pos (->> (range 0 (count (:frames sprite)))
                              (map (fn [frame-idx] {:frame-idx frame-idx :layer-idx idx})))
        new-selected-cels-pos (apply disj (:selected-cels-pos sprite) removed-cels-pos)
        new-linked-cel-groups (apply dissoc (:linked-cel-groups sprite) removed-cels-pos)]
    (-> sprite
        (assoc :layers new-layers)
        (assoc :cels new-cels)
        (assoc :linked-cel-groups new-linked-cel-groups)
        (assoc :selected-cels-pos new-selected-cels-pos)
        (#(if (= idx (get-current-layer-idx sprite))
            (select-cel {:frame-idx (:frame-idx current-cel-pos)
                         :layer-idx (min (dec (:layer-idx current-cel-pos))
                                         (dec (count (:layers %))))} %)
            %)))))

(defn duplicate-layer [sprite]
  (let [{:keys [layers]} sprite
        current-layer-idx (get-current-layer-idx sprite)
        current-layer (nth layers current-layer-idx)]
    (add-layer current-layer
               (fn [pos] (get-cel {:frame-idx (:frame-idx pos)
                                   :layer-idx current-layer-idx} sprite))
               sprite)))

;; todo: обновить linked-cel-groups
(defn- move-layer [from-idx to-idx sprite]
  (let [new-layers (coll/swapv from-idx to-idx (:layers sprite))
        new-cels (coll/update-byv #(= (-> :pos :layer-idx) from-idx)
                                  (fn [cel] (assoc-in cel [:pos :layer-idx] to-idx))
                                  (:cels sprite))
        new-selected-cels-pos (set (coll/update-byv #(= (:layer-idx %) from-idx)
                                                    #(assoc % :layer-idx to-idx)
                                                    (:selected-cels-pos sprite)))]
    (-> sprite
        (assoc :layers new-layers)
        (assoc :cels new-cels)
        (assoc :selected-cels-pos new-selected-cels-pos)
        (#(select-layer to-idx %)))))

(defn move-layer-up [idx sprite]
  (move-layer idx (dec idx) sprite))

(defn move-layer-down [idx sprite]
  (move-layer idx (inc idx) sprite))

;; todo: linked cels 
(defn merge-layer-with-below [sprite]
  (if (and (< (get-current-layer-idx sprite) (count (:layers sprite)))
           (> (count (:layers sprite)) 1))
    (let [current-layer-idx (get-current-layer-idx sprite)
          below-layer-idx (inc current-layer-idx)
          sprite-with-updated-cels
          (reduce (fn [sprite frame-idx]
                    (update-cel #(cel/merge-cels (get-cel {:frame-idx frame-idx
                                                           :layer-idx below-layer-idx}
                                                          sprite)
                                                 %)
                                {:frame-idx frame-idx :layer-idx current-layer-idx}
                                sprite))
                  sprite
                  (range 0 (count (:frames sprite))))]
      (->> sprite-with-updated-cels
           (remove-layer below-layer-idx)))
    sprite))

(defn update-layer [idx f sprite]
  (update-in sprite [:layers idx] f))

(defn get-current-layer [sprite]
  (-> sprite :layers (nth (get-current-layer-idx sprite))))

(defn add-frame
  ([frame sprite]
   (add-frame frame (fn [pos] (cel/create (get-size sprite) pos)) sprite))
  ([frame create-cel sprite]
   (let [current-frame-idx (get-current-frame-idx sprite)
         new-current-frame-idx (inc current-frame-idx)
         new-cels (mapv (fn [layer-idx] (create-cel {:frame-idx new-current-frame-idx
                                                     :layer-idx layer-idx}))
                        (range 0 (count (:layers sprite))))]
     (-> sprite
         (update :frames #(coll/insertv new-current-frame-idx frame %))
         (#(select-frame new-current-frame-idx %))
         (update :cels #(into % new-cels))))))

;; todo: cannot be empty
(defn remove-frame [sprite]
  (let [{:keys [layers linked-cel-groups]} sprite
        current-cel-pos (get-current-cel-pos sprite)
        removed-cels-pos (->> (range 0 (count layers))
                              (map (fn [layer-idx] {:frame-idx (:frame-idx current-cel-pos)
                                                    :layer-idx layer-idx})))
        new-selected-cels-pos (apply disj (:selected-cels-pos sprite)
                                     removed-cels-pos)
        new-linked-cel-groups (apply (partial dissoc linked-cel-groups) removed-cels-pos)]
    (-> sprite
        (update :frames #(coll/removev (:frame-idx current-cel-pos) %))
        (update :cels (fn [cels] (vec (remove #(= (-> % :pos :frame-idx) (:frame-idx current-cel-pos)) cels))))
        (assoc :linked-cel-groups new-linked-cel-groups)
        (assoc :selected-cels-pos new-selected-cels-pos)
        (#(select-cel {:frame-idx (min (dec (:frame-idx current-cel-pos))
                                       (dec (count (:frames %))))
                       :layer-idx (:layer-idx current-cel-pos)} %)))))

(defn duplicate-frame [sprite]
  (let [current-frame (nth (:frames sprite) (get-current-frame-idx sprite))
        current-frame-idx (get-current-frame-idx sprite)]
    (add-frame current-frame
               (fn [pos] (-> (get-cel {:frame-idx current-frame-idx
                                       :layer-idx (:layer-idx pos)}
                                      sprite)
                             (assoc :pos pos)))
               sprite)))

;; todo: обновить linked; обновить selected-cels
(defn move-frame [from to sprite]
  (-> sprite
      (update :frames #(coll/swapv from to %))
      (update :cels #(coll/swapv from to %))
      (#(select-frame to %))))
