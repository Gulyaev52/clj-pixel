(ns pixel-art.new-project-modal
  (:require [pixel-art.default-project :as default-project]
            [re-frame.core :as re-frame]))

(defn init [opened]
  {:opened opened
   :size {:width 100 :height 100}})

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (assoc-in db [:new-project-modal :opened] opened)}))

(re-frame/reg-event-fx
 ::set-size
 (fn [{:keys [db]} [_ size]]
   {:db (assoc-in db [:new-project-modal :size] size)}))

(re-frame/reg-event-fx
 ::create-example-project
 (fn []
   {:fx [[:dispatch
          [:start-new-project default-project/example-project]]]}))

(re-frame/reg-event-fx
 ::create
 (fn [{:keys [db]}]
   {:fx [[:dispatch
          [:start-new-project
           (assoc default-project/default-palettes-and-current-colors
                  :sprite (default-project/create-empty-sprite (-> db :new-project-modal :size)))]]]}))
