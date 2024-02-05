(ns pixel-art.model.sprite)

(defn create [size]
  {:size size
   :active-frame-idx 0
   :frames []})

(defn resize [sprite])

(defn add-frame [frame sprite])

(defn remove-frame [idx sprite])

(defn duplicate-frame [idx sprite])

(defn move-frame [{:keys [from to]} sprite])

(defn update-current-frame [f sprite])

(defn get-current-frame [sprite])
