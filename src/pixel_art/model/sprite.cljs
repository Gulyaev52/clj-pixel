(ns pixel-art.model.sprite
  (:require [pixel-art.utils.coll :as coll]
            [sc.api]))

(defn create [size]
  {:size size
   :current-frame-idx 0
   :frames []})

(defn- swapv
  [i j v]
  (assoc v i (nth v j) j (nth v i)))

(defn resize [sprite])

(defn add-frame [frame sprite]
  (-> sprite
      (update :frames #(conj % frame))
      (#(assoc % :current-frame-idx (dec (count (:frames %)))))))

;; todo: cannot be empty
(defn remove-frame [idx sprite]
  (-> sprite
      (update :frames #(coll/removev idx %))
      (update :current-frame-idx (fn [v]
                                   (if (<= idx v)
                                     (max (dec v) 0)
                                     v)))))

(defn duplicate-frame [idx sprite]
  (let [current-frame (nth (:frames sprite) idx)
        duplicated-frame-pos (inc idx)]
    (-> sprite
        (update :frames #(coll/insertv duplicated-frame-pos current-frame %))
        (assoc :current-frame-idx duplicated-frame-pos)))) ;;todo: wrong pos

(defn move-frame [from to sprite]
  (-> sprite
      (update :frames #(swapv from to %))
      (assoc :current-frame-idx to)))

(defn update-current-frame [f sprite]
  (update sprite
          :frames
          #(update % (:current-frame-idx sprite) f)))

(defn select-frame [idx sprite]
  (assoc sprite :current-frame-idx idx))

(defn get-current-frame [{:keys [current-frame-idx frames]}]
  (nth frames current-frame-idx))
