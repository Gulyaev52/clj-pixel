(ns pixel-art.history
  (:require [pixel-art.model.sprite :as sprite]
            [re-frame.core :as re-frame]))

(def stack-max-size 200)

(defn init [{:keys [sprite]}]
  {:current-idx 0
   :stack [{:sprite sprite}]})

(def hotkeys [[[::undo]
               [{:keyCode 90 ;; z
                 :ctrlKey true}]]

              [[::redo]
               [{:keyCode 89 ;; y
                 :ctrlKey true}]]])

(defn save-frame [db changes updated-frame]
  (if (seq changes)
    (let [{:keys [history]} db]
      (-> history
          (update :stack #(if (= (count %) stack-max-size)
                            (take-last stack-max-size %)
                            %))
          (update :stack #(if (not= (:current-idx history) (- (count %) 1))
                            (subvec % 0 (inc (:current-idx history)))
                            %))
          (update :stack #(conj % {:frame updated-frame}))
          (update :current-idx inc)
          (#(assoc db :history %))))
    db))

(defn- restore [db idx]
  (let [{:keys [history]} db
        {:keys [stack]} history
        changes (nth stack idx)]
    (cond
      (:sprite changes)
      (assoc db :sprite (:sprite changes))

      (:frame changes)
      (update db :sprite (fn [s]
                           (sprite/update-current-frame (fn [_] (:frame changes)) s))))))

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

(re-frame/reg-event-fx
 ::undo
 (fn [{:keys [db]} _]
   (let [updated-db (undo db)
         current-frame (sprite/get-current-frame (:sprite updated-db))]
     {:db updated-db
      :fx [[:draw-frame current-frame]]})))

(re-frame/reg-event-fx
 ::redo
 (fn [{:keys [db]} _]
   (let [updated-db (redo db)
         current-frame (sprite/get-current-frame (:sprite updated-db))]
     {:db updated-db
      :fx [[:draw-frame current-frame]]})))
