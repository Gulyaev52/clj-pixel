(ns pixel-art.sprite-preview
  (:require [re-frame.core :as re-frame]))

(defn init []
  {:size :default
   :opened false
   :displayed-frame-idx 0})

(re-frame/reg-event-fx
 ::change-size
 (fn [{:keys [db]} [_ size]]
   {:db (assoc-in db [:sprite-preview :size] size)}))

(defn- dispatch-display-next-frame [idx db]
  (let [duration (-> db :sprite :frames (nth idx) :duration)]
    [:dispatch-later {:ms duration
                      :dispatch [::display-next-frame]}]))

(re-frame/reg-event-fx
 ::open
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sprite-preview :opened] true)
    :fx (when (> (count (:frame-imgs db)) 1)
          [(dispatch-display-next-frame 0 db)])}))

(re-frame/reg-event-fx
 ::display-next-frame
 (fn [{:keys [db]} _]
   (let [{:keys [opened displayed-frame-idx]} (:sprite-preview db)]
     (if opened
       (let [next-idx (let [next-idx (inc displayed-frame-idx)]
                        (if (contains? (:frame-imgs db) next-idx) next-idx 0))]
         {:db (assoc-in db [:sprite-preview :displayed-frame-idx] next-idx)
          :fx [(dispatch-display-next-frame next-idx db)]})
       {:db (assoc-in db [:sprite-preview :displayed-frame-idx] 0)}))))

(re-frame/reg-event-fx
 ::close
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sprite-preview :opened] false)}))
