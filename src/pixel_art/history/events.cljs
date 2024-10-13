(ns pixel-art.history.events
  (:require [pixel-art.tool.core :as tool]
            [re-frame.core :as re-frame]
            [pixel-art.history :as history]))

(re-frame/reg-event-fx
 ::undo
 (fn [{:keys [db]} _]
   (if (history/check-undo-available? db)
     {:db (-> db
              (assoc :tool (tool/init (-> db :tool :type)))
              history/undo)
      :fx [[:clear-preview]]}
     {:db db})))

(re-frame/reg-event-fx
 ::redo
 (fn [{:keys [db]} _]
   (if (history/check-redo-available? db)
     {:db (-> db
              (assoc :tool (tool/init (-> db :tool :type)))
              history/redo)
      :fx [[:clear-preview]]}
     {:db db})))
