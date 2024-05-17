(ns pixel-art.history
  (:require [pixel-art.model.sprite :as sprite]
            [re-frame.core :as re-frame]))

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

(defn- restore [db idx]
  (let [{:keys [history]} db
        {:keys [stack]} history
        changes (nth stack idx)]
    (cond
      (:sprite changes)
      (assoc db :sprite (:sprite changes))

      (:frame changes)
      (update db :sprite #(->> %
                               (sprite/select-frame (:current-frame-idx changes))
                               (sprite/update-current-frame (fn [_] (:frame changes))))))))

(defn check-undo-available? [db]
  (> (get-in db [:history :current-idx]) 0))

(defn undo [db]
  (if (check-undo-available? db)
    (-> db
        (restore (dec (get-in db [:history :current-idx])))
        (update-in [:history :current-idx] dec))
    db))

(defn check-redo-available? [db]
  (< (inc (get-in db [:history :current-idx]))
     (count (get-in db [:history :stack]))))

(defn redo [db]
  (if (check-redo-available? db)
    (-> db
        (restore (inc (get-in db [:history :current-idx])))
        (update-in [:history :current-idx] inc))
    db))
