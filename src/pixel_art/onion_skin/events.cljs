(ns pixel-art.onion-skin.events
  (:require
   [re-frame.core :as re-frame]))

(defn init []
  {:enabled false
   :position :front ;; front|behind
   :opacity 0.3
   :frames-count {:prev 1 :next 1}})

(re-frame/reg-event-fx
 ::set-frames-count
 (fn [{:keys [db]} [_ frames-count]]
   {:db (assoc-in db [:onion-skin :frames-count] frames-count)}))

(re-frame/reg-event-fx
 ::set-enabled
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc-in db [:onion-skin :enabled] enabled)}))

(re-frame/reg-event-fx
 ::set-opacity
 (fn [{:keys [db]} [_ opacity]]
   {:db (assoc-in db [:onion-skin :opacity] opacity)}))

(re-frame/reg-event-fx
 ::set-position
 (fn [{:keys [db]} [_ position]]
   {:db (assoc-in db [:onion-skin :position] position)}))
