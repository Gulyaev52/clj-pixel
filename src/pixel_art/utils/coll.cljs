(ns pixel-art.utils.coll)

(defn removev
  "remove elem in coll"
  [pos coll]
  (into (subvec coll 0 pos) (subvec coll (inc pos))))

(defn insertv [index elem coll]
  (let [[l r] (split-at index coll)]
    (->> (concat l [elem] r)
         vec)))

(defn swapv
  [i j v]
  (assoc v i (nth v j) j (nth v i)))

(defn find-first [pred coll]
  (first (filter pred coll)))

(defn find-first-idx [pred coll]
  (ffirst (filter (fn [[_ v]] (pred v)) (map-indexed vector coll))))

(defn update-byv [pred f coll]
  (mapv
   (fn [item]
     (if (pred item)
       (f item)
       item))
   coll))

;; todo: map-cels
(defn map-matrix [f matrix]
  (vec (map-indexed (fn [y layer-cels]
                      (vec (map-indexed (fn [x cel]
                                          (f cel {:y y :x x}))
                                        layer-cels)))
                    matrix)))
