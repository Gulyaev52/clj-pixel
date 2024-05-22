(ns pixel-art.utils.coll)

(defn removev
  "remove elem in coll"
  [pos coll]
  (into (subvec coll 0 pos) (subvec coll (inc pos))))

(defn insertv [index elem coll]
  (let [[l r] (split-at index coll)]
    (->> (concat l [elem] r)
         vec)))
