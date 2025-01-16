(ns pixel-art.keyboard-shortcuts-modal.events
  (:require
   [re-frame.core :as re-frame]))

(defn init []
  {:opened false})

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (assoc-in db [:keyboard-shortcuts-modal :opened] opened)}))
