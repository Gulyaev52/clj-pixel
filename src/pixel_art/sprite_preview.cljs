(ns pixel-art.sprite-preview
  (:require [pixel-art.canvas :as canvas]
            [pixel-art.model.sprite :as sprite]
            [re-frame.core :as re-frame]))

(defn init []
  {:size :default
   :opened false
   :displayed-frame-idx 0
   :frame-imgs {}})

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
   (let [sprite (:sprite db)
         frames (:frames sprite)
         frame-imgs (->> (for [frame-idx (range 0 (count frames))]
                           [frame-idx
                            (canvas/generate-img
                             #(canvas/draw-frame-on-single-canvas frame-idx sprite %)
                             (sprite/get-size sprite))])
                         (into {}))]
     {:db (-> db
              (assoc-in [:sprite-preview :opened] true)
              (assoc-in [:sprite-preview :frame-imgs] frame-imgs))
      :fx (when (> (count frames) 1)
            [(dispatch-display-next-frame 0 db)])})))

(re-frame/reg-event-fx
 ::display-next-frame
 (fn [{:keys [db]} _]
   (let [{:keys [opened displayed-frame-idx]} (:sprite-preview db)
         sprite (:sprite db)]
     (if opened
       (let [next-idx (let [next-idx (inc displayed-frame-idx)]
                        (if ((set (range (count (:frames sprite)))) next-idx) next-idx 0))]
         {:db (assoc-in db [:sprite-preview :displayed-frame-idx] next-idx)
          :fx [(dispatch-display-next-frame next-idx db)]})
       {:db (assoc-in db [:sprite-preview :displayed-frame-idx] 0)}))))

(re-frame/reg-event-fx
 ::close
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:sprite-preview :opened] false)}))
