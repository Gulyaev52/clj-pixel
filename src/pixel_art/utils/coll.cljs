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

(defn update-byv [pred f coll]
  (map
   (fn [item]
     (if (pred item)
       (f item)
       item))
   coll))
