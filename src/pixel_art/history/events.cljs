(ns pixel-art.history.events
  (:require [pixel-art.tool.core :as tool]
            [re-frame.core :as re-frame]
            [pixel-art.history :as history]))

(re-frame/reg-event-fx
 ::undo
 [(re-frame/inject-cofx :viewport-size)]
 (fn [{:keys [db viewport-size]} _]
   (if (history/check-undo-available? db)
     {:db (-> db
              (assoc :tool (tool/init (-> db :tool :type)))
              (history/undo viewport-size))}
     {:db db})))

(re-frame/reg-event-fx
 ::redo
 [(re-frame/inject-cofx :viewport-size)]
 (fn [{:keys [db viewport-size]} _]
   (if (history/check-redo-available? db)
     {:db (-> db
              (assoc :tool (tool/init (-> db :tool :type)))
              (history/redo viewport-size))}
     {:db db})))
