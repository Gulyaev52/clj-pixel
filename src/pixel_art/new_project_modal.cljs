(ns pixel-art.new-project-modal
  (:require [pixel-art.project-settings :as project-settings]
            [re-frame.core :as re-frame]))

(defn init [opened]
  {:opened opened
   :size {:width 100 :height 100}})

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (-> db
            (assoc-in [:new-project-modal :opened] opened)
            (assoc-in [:new-project-modal :size] (-> db :sprite :size)))}))

(re-frame/reg-event-fx
 ::set-width
 (fn [{:keys [db]} [_ width]]
   {:db (assoc-in db [:new-project-modal :size :width] width)}))

(re-frame/reg-event-fx
 ::set-height
 (fn [{:keys [db]} [_ height]]
   {:db (assoc-in db [:new-project-modal :size :height] height)}))

(re-frame/reg-event-fx
 ::create-example-project
 (fn []
   {:fx [[:dispatch
          [:start-new-project project-settings/example-project]]]}))

(re-frame/reg-event-fx
 ::create
 (fn [{:keys [db]}]
   {:fx [[:dispatch
          [:start-new-project
           (assoc project-settings/default-palettes-and-current-colors
                  :sprite (project-settings/create-empty-sprite (-> db :new-project-modal :size)))]]]}))
