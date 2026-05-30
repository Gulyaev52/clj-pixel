(ns pixel-art.db.utils 
  (:require
    [pixel-art.drawing.initial-settings :refer [get-initial-drawing-settings]]))

(defn mark-unsaved-changes-saved [db]
  (let [current-idx (-> db :history :current-idx)]
    (assoc db :last-saved-history-idx current-idx)))

(defn check-unsaved-changes-exist [db]
  (not= (:last-saved-history-idx db) (-> db :history :current-idx)))

(defn set-sprite [db sprite {:keys [prev-sprite viewport-size]}]
  (-> db
      (assoc :sprite sprite)
      (#(if (not= (:size sprite) (:size prev-sprite))
          (merge % (get-initial-drawing-settings (:size sprite) viewport-size))
          %))))
