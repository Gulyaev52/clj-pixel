(ns pixel-art.preview
  (:require [re-frame.core :as re-frame]))

(defn init []
  {:size :default
   :frame-speed 500
   :opened false
   :displayed-frame-idx 0})

(re-frame/reg-event-fx
 ::change-size
 (fn [{:keys [db]} [_ size]]
   {:db (assoc-in db [:preview-settings :size] size)}))

(re-frame/reg-event-fx
 ::change-frame-speed
 (fn [{:keys [db]} [_ frame-speed]]
   {:db (assoc-in db [:preview-settings :frame-speed] frame-speed)}))

(re-frame/reg-event-fx
 ::open
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:preview-settings :opened] true)
    :fx [[:dispatch-later {:ms (-> db :preview-settings :frame-speed)
                           :dispatch [::display-next-frame]}]]}))

(re-frame/reg-event-fx
 ::display-next-frame
 (fn [{:keys [db]} _]
   (let [{:keys [opened displayed-frame-idx]} (:preview-settings db)]
     (if opened
       (let [next-idx (let [next-idx (inc displayed-frame-idx)]
                        (if (contains? (:frame-imgs db) next-idx) next-idx 0))]
         {:db (assoc-in db [:preview-settings :displayed-frame-idx] next-idx)
          :fx [[:dispatch-later {:ms (-> db :preview-settings :frame-speed)
                                 :dispatch [::display-next-frame]}]]})
       {:db (assoc-in db [:preview-settings :displayed-frame-idx] 0)}))))

(re-frame/reg-event-fx
 ::close
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:preview-settings :opened] false)}))
