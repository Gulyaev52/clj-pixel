(ns pixel-art.history
  (:require
   [pixel-art.db :as db]))

(def stack-max-size 200)

(defn init [{:keys [sprite]}]
  {:current-idx 0
   :stack [{:sprite sprite}]})

(defn- save-state [db history-item]
  (let [{:keys [history]} db]
    (-> history
        (update :stack #(if (= (count %) stack-max-size)
                          (take-last stack-max-size %)
                          %))
        (update :stack #(if (not= (:current-idx history) (- (count %) 1))
                          (subvec % 0 (inc (:current-idx history)))
                          %))
        (update :stack #(conj % history-item))
        (update :current-idx inc)
        (#(assoc db :history %)))))

(defn save-sprite [db]
  (save-state db {:sprite (:sprite db)}))

(defn- restore [db viewport-size idx]
  (let [{:keys [history]} db
        {:keys [stack]} history
        changes (nth stack idx)]
    (db/set-sprite db (:sprite changes) {:prev-sprite (:sprite db)
                                         :viewport-size viewport-size})))

(defn check-undo-available? [db]
  (> (get-in db [:history :current-idx]) 0))

(defn undo [db viewport-size]
  (-> db
      (restore viewport-size (dec (get-in db [:history :current-idx])))
      (update-in [:history :current-idx] dec)))

(defn check-redo-available? [db]
  (< (inc (get-in db [:history :current-idx]))
     (count (get-in db [:history :stack]))))

(defn redo [db viewport-size]
  (-> db
      (restore viewport-size (inc (get-in db [:history :current-idx])))
      (update-in [:history :current-idx] inc)))
