(ns pixel-art.model.layer)

(defn create [name children]
  {:visibile? true
   :locked? false
   :automatic-linking? false
   :name name
   :children children})

(defn editable? [layer]
  (and (:visibile? layer) (not (:locked? layer))))

(defn group? [layer]
  (some? (:children layer)))
