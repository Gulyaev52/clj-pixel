(ns pixel-art.model.sprite
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.utils.coll :as coll]
            [sc.api]))

(defn create [{:keys [size layer frame cel]}]
  {:size size
   :frames [frame]
   :cels [[(assoc cel
                  :current true
                  :selected true)]]
   :layers [layer]})

(defn- update-cel [f pos sprite]
  (update-in sprite [:cels (:frame-idx pos) (:layer-idx pos)] f))

(defn- update-cels [f cels-pos sprite]
  (reduce (fn [res-sprite cel-pos] (update-cel f cel-pos res-sprite))
          sprite
          cels-pos))

(defn- map-cels [f cels]
  (vec (map-indexed (fn [frame-idx layer-cels]
                      (vec (map-indexed (fn [layer-idx cel]
                                          (f cel {:frame-idx frame-idx :layer-idx layer-idx}))
                                        layer-cels)))
                    cels)))

(defn get-cels-with-pos-as-coll [sprite]
  (flatten
   (map-cels (fn [cel pos] (assoc cel :pos pos)) (:cels sprite))))

(defn get-current-cel-pos [sprite]
  (->> sprite
       get-cels-with-pos-as-coll
       (coll/find-first :current)
       :pos))

(defn select-only-1-cel [target-cel-pos sprite]
  (let [new-cels (map-cels (fn [cel pos]
                             (assoc cel
                                    :current (= target-cel-pos pos)
                                    :selected (= target-cel-pos pos)))
                           (:cels sprite))]
    (assoc sprite :cels new-cels)))

;; todo: rename
(defn- select-cel [cel-pos sprite]
  (let [current-cel-pos (get-current-cel-pos sprite)]
    (->> sprite
         (#(if current-cel-pos
             (update-cel (fn [c] (assoc c :current false)) current-cel-pos %)
             %))
         (update-cel (fn [c] (assoc c :current true :selected true)) cel-pos))))

(defn get-selected-cels [sprite]
  (->> sprite
       get-cels-with-pos-as-coll
       (filter :selected)))

(defn get-selected-cels-pos [sprite]
  (->> sprite
       get-cels-with-pos-as-coll
       (filter :selected)
       (map :pos)
       set))

(defn toggle-cel-to-selection [cel-pos sprite]
  (let [selected-cels-pos (get-selected-cels-pos sprite)
        current-cel-pos (get-current-cel-pos sprite)]
    (cond
      (and (= (count selected-cels-pos) 1)
           (= cel-pos current-cel-pos))
      sprite

      (selected-cels-pos cel-pos)
      (let [current-cel-pos (if (= current-cel-pos cel-pos)
                              (first selected-cels-pos)
                              current-cel-pos)]
        (->> sprite
             (select-cel current-cel-pos)
             (update-cel (fn [c] (assoc c :selected false :current false)) cel-pos)))

      :else (select-cel cel-pos sprite))))

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
    (->> (reduce #(select-cel %2 %1) sprite new-selected-cels)
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
  (nth (:cels sprite) frame-idx))

(defn get-frame-cels-with-layers [frame-idx sprite]
  (let [{:keys [layers]} sprite]
    (->> (get-frame-cels frame-idx sprite)
         (map-indexed (fn [idx cel]
                        (assoc cel :layer (nth layers idx)))))))

;; todo: rename; do we need get-frame-cels-with-layers, get-frame-cels?
;; todo: они сгруппированы по фреймам а в назв не отражено
(defn get-denormalized-cels [sprite]
  (let [{:keys [cels layers frames]} sprite]
    (->> cels
         (coll/map-matrix (fn [cel {:keys [x y]}]
                            (assoc cel
                                   :layer (nth layers x)
                                   :frame (nth frames y)
                                   :pos {:frame-idx y
                                         :layer-idx x}))))))

(defn get-size [sprite] (:size sprite))

(defn get-cel [pos sprite]
  (get-in (:cels sprite) [(:frame-idx pos) (:layer-idx pos)]))

(defn get-current-cel [sprite]
  (get-cel (get-current-cel-pos sprite) sprite))

(defn get-current-frame [sprite]
  (nth (:frames sprite) (get-current-frame-idx sprite)))

(defn- update-current-cel-and-linked [f sprite]
  (let [updated-current-cel (f (get-current-cel sprite))
        current-cel-pos (get-current-cel-pos sprite)
        current-cel (get-current-cel sprite)
        linked-cels-pos (->> (get-cels-with-pos-as-coll sprite)
                             (filter #(and (= (-> % :pos :layer-idx) (:layer-idx current-cel-pos))
                                           (if (some? (:group-number current-cel))
                                             (= (:group-number %) (:group-number current-cel))
                                             false)))
                             (map :pos))]
    (update-cels (fn [_] updated-current-cel) ;; todo: current, selected?
                 (conj (set linked-cels-pos) current-cel-pos)
                 sprite)))

(defn set-current-cel-pixels [pixels-map sprite]
  (update-current-cel-and-linked #(cel/set-pixels pixels-map %) sprite))

(defn set-current-cel-opacity [opacity sprite]
  (update-current-cel-and-linked #(assoc % :opacity opacity) sprite))

(defn link-layer-cels [cels-pos main-cel-pos sprite]
  (let [group-number (or (:group-number (get-cel main-cel-pos sprite))
                         (some->> (:cels sprite)
                                  (map #(nth % (:layer-idx main-cel-pos)))
                                  (keep :group-number)
                                  (apply max)
                                  inc)
                         0)
        main-cel (get-cel main-cel-pos sprite)]
    (update-cels (fn [cel] (assoc cel
                                  :opacity (:opacity main-cel)
                                  :pixels (:pixels main-cel)
                                  :group-number group-number))
                 cels-pos
                 sprite)))

(defn link-selected-cels [main-cel-pos sprite]
  (let [selected-cels-pos (get-selected-cels-pos sprite)
        layer-selected-cels-pos (filter #(= (:layer-idx %) (:layer-idx main-cel-pos)) selected-cels-pos)]
    (link-layer-cels layer-selected-cels-pos main-cel-pos sprite)))

(defn unlink-selected-cels [sprite]
  (let [selected-cels-pos (get-selected-cels-pos sprite)]
    (update-cels #(dissoc % :group-number) selected-cels-pos sprite)))

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
   (let [current-layer-idx (get-current-layer-idx sprite)
         new-current-layer-idx (inc current-layer-idx)
         new-layers (coll/insertv new-current-layer-idx layer (:layers sprite))
         new-cels (->> (map-indexed (fn [frame-idx row]
                                      (coll/insertv new-current-layer-idx (create-new-cel {:frame-idx frame-idx :layer-idx new-current-layer-idx}) row))
                                    (:cels sprite))
                       vec)]
     (-> sprite
         (assoc :layers new-layers)
         (assoc :cels new-cels)
         (#(select-layer new-current-layer-idx %))))))

(defn remove-layer-available? [sprite]
  (> (-> sprite :layers count) 1))

(defn remove-layer [idx sprite]
  (let [new-layers (coll/removev idx (:layers sprite))
        current-cel-pos (get-current-cel-pos sprite)
        new-current-cel-pos (if (= idx (:layer-idx current-cel-pos))
                              {:frame-idx (:frame-idx current-cel-pos)
                               :layer-idx (max (dec (:layer-idx current-cel-pos)) 0)}
                              current-cel-pos)
        new-cels (->> (:cels sprite)
                      (mapv #(coll/removev idx %)))]
    (-> sprite
        (assoc :layers new-layers)
        (assoc :cels new-cels)
        (#(select-cel new-current-cel-pos %)))))

(defn duplicate-layer [sprite]
  (let [{:keys [layers]} sprite
        current-layer-idx (get-current-layer-idx sprite)
        current-layer (nth layers current-layer-idx)]
    (add-layer current-layer
               (fn [pos] (get-cel {:frame-idx (:frame-idx pos)
                                   :layer-idx current-layer-idx} sprite))
               sprite)))

(defn- move-layer [from-idx to-idx sprite]
  (let [new-layers (coll/swapv from-idx to-idx (:layers sprite))
        new-cels (map (fn [cels] (coll/swapv from-idx to-idx cels)) (:cels sprite))]
    (-> sprite
        (assoc :layers new-layers)
        (assoc :cels new-cels)
        (#(select-layer to-idx %)))))

(defn move-layer-up-available? [layer-idx sprite]
  (> layer-idx 0))

(defn move-layer-up [idx sprite]
  (move-layer idx (dec idx) sprite))

(defn move-layer-down-available? [layer-idx sprite]
  (<= layer-idx (- (count (:layers sprite)) 2)))

(defn move-layer-down [idx sprite]
  (move-layer idx (inc idx) sprite))

(defn merge-layer-with-below-available? [sprite]
  (< (get-current-layer-idx sprite) (dec (count (:layers sprite)))))

(defn merge-layer-with-below [sprite]
  (let [current-layer-idx (get-current-layer-idx sprite)
        below-layer-idx (inc current-layer-idx)
        new-cels (map (fn [cel-row]
                        (update cel-row
                                current-layer-idx
                                #(cel/merge-cels (nth cel-row below-layer-idx) %)))
                      (:cels sprite))]
    (->> (assoc sprite :cels new-cels)
         (remove-layer below-layer-idx))))

(defn update-layer [idx f sprite]
  (update-in sprite [:layers idx] f))

(defn update-frame [idx f sprite]
  (update-in sprite [:frames idx] f))

(defn add-frame
  ([frame sprite]
   (add-frame frame (fn [] (cel/create (get-size sprite))) sprite))
  ([frame create-cel sprite]
   (let [{:keys [layers cels]} sprite
         current-frame-idx (get-current-frame-idx sprite)
         new-current-frame-idx (inc current-frame-idx)
         new-cels (->> (mapv #(create-cel {:frame-idx new-current-frame-idx
                                           :layer-idx %})
                             (range (count layers)))
                       (#(coll/insertv new-current-frame-idx % cels)))
         layer-ids-with-auto-linking (->> layers
                                          (map-indexed vector)
                                          (filter (fn [[_ l]] (:automatic-linking? l)))
                                          (map first))]
     (-> sprite
         (update :frames #(coll/insertv new-current-frame-idx frame %))
         (assoc :cels new-cels)
         (#(select-frame new-current-frame-idx %))
         (#(reduce (fn [res layer-idx]
                     (link-layer-cels [{:frame-idx current-frame-idx
                                        :layer-idx layer-idx}
                                       {:frame-idx new-current-frame-idx
                                        :layer-idx layer-idx}]
                                      {:frame-idx current-frame-idx
                                       :layer-idx layer-idx}
                                      res))
                   %
                   layer-ids-with-auto-linking))))))

(defn remove-frame-available? [sprite]
  (> (-> sprite :frames count) 1))

(defn remove-frame [sprite]
  (let [current-cel-pos (get-current-cel-pos sprite)
        new-frames (coll/removev (:frame-idx current-cel-pos) (:frames sprite))
        new-current-cel-pos {:frame-idx (max (dec (:frame-idx current-cel-pos)) 0)
                             :layer-idx (:layer-idx current-cel-pos)}
        new-cels (->> (:cels sprite)
                      (coll/removev (:frame-idx current-cel-pos))
                      vec)]
    (-> sprite
        (assoc :frames new-frames)
        (assoc :cels new-cels)
        (#(select-cel new-current-cel-pos %)))))

(defn duplicate-frame [sprite]
  (let [current-frame (nth (:frames sprite) (get-current-frame-idx sprite))
        current-frame-idx (get-current-frame-idx sprite)]
    (add-frame current-frame
               (fn [pos] (get-cel {:frame-idx current-frame-idx
                                   :layer-idx (:layer-idx pos)}
                                  sprite))
               sprite)))

;; todo: обновить linked; обновить selected-cels
(defn move-frame [from to sprite]
  (-> sprite
      (update :frames #(coll/swapv from to %))
      (update :cels #(coll/swapv from to %))
      (#(select-frame to %))))
