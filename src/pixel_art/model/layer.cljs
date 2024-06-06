(ns pixel-art.model.layer)

(defn create [name children]
  {:visibile? true
   :locked? false
   :automatic-linking? false
   :name name
   :children children})

(defn group? [layer]
  (some? (:children layer)))
