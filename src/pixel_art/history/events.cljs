(ns pixel-art.history.events
  (:require [pixel-art.tool.core :as tool]
            [re-frame.core :as re-frame]
            [pixel-art.history :as history]))

(re-frame/reg-event-fx
 ::undo
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc :tool (tool/init (-> db :tool :type)))
            history/undo)
    :fx [[:clear-preview]]})) ;; todo: fix

(re-frame/reg-event-fx
 ::redo
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc :tool (tool/init (-> db :tool :type)))
            history/redo)
    :fx [[:clear-preview]]}))
