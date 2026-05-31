(ns pixel-art.new-project-modal.events
  (:require [pixel-art.example-project :as example-project]
            [pixel-art.project-config :as project-config]
            [pixel-art.project-save-load.sprite-serialization :as sprite-serialization]
            [re-frame.core :as re-frame]))

(re-frame/reg-fx
 ::load-example-project
 (fn []
   (.. (sprite-serialization/deserialize+ example-project/github-octopus-sprite)
       (then (fn [sprite]
               (assoc project-config/default-palettes-and-current-colors :sprite sprite)))
       (then (fn [sprite]
               (re-frame/dispatch [:initialize-db sprite])))
       (catch (fn [_]
                (re-frame/dispatch [:show-notification {:type "error"
                                                        :message "Failed to load example project"}]))))))

(defn init [opened]
  {:opened opened
   :settings {:title "Untitled" :size {:width 100 :height 100}}})

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (-> db
            (assoc-in [:new-project-modal :opened] opened)
            (assoc-in [:new-project-modal :settings :size] (-> db :sprite :size)))}))

(re-frame/reg-event-fx
 ::set-width
 (fn [{:keys [db]} [_ width]]
   {:db (assoc-in db [:new-project-modal :settings :size :width] width)}))

(re-frame/reg-event-fx
 ::set-height
 (fn [{:keys [db]} [_ height]]
   {:db (assoc-in db [:new-project-modal :settings :size :height] height)}))

(re-frame/reg-event-fx
 ::set-title
 (fn [{:keys [db]} [_ title]]
   {:db (assoc-in db [:new-project-modal :settings :title] title)}))

(re-frame/reg-event-fx
 ::create-example-project
 (fn []
   {:fx [[::load-example-project]]}))

(re-frame/reg-event-fx
 ::create
 (fn [{:keys [db]}]
   (let [settings (-> db :new-project-modal :settings)]
     {:fx [[:dispatch
            [:initialize-db
             (assoc project-config/default-palettes-and-current-colors
                    :sprite (project-config/create-empty-sprite (:title settings) (:size settings)))]]]})))
