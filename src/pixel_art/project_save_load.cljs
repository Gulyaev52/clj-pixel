(ns pixel-art.project-save-load
  (:require [re-frame.core :as re-frame]
            [pixel-art.project-serialization :as project-serialization]))

(def sprite-file-ext "json")

(defn- sprite->file-desc [sprite]
  (let [exported-project (project-serialization/serialize {:sprite sprite})]
    {:file-name (str "pixel-project."  sprite-file-ext)
     :content (-> {:version "1" :project exported-project}
                  clj->js
                  (#(. js/JSON stringify %)))
     :content-type :json}))

(re-frame/reg-event-fx
 ::save-as-file
 (fn [{:keys [db]}]
   (let [file-desc (sprite->file-desc (:sprite db))]
     {:fx [[:download-file file-desc]]})))

(re-frame/reg-event-fx
 ::load-from-file
 (fn [_ [_ file-desc]]
   {:fx [[::deserialize file-desc]]}))

(re-frame/reg-event-fx
 ::deserialize-success
 (fn [{:keys [db]} [_ sprite]]
   {:fx [[:dispatch [:pixel-art.events/initialize-db
                     (merge {:sprite sprite}
                            (select-keys db [:palettes :primary-color :secondary-color]))]]]}))

(re-frame/reg-event-fx
 ::deserialize-error
 (fn [_ [_ error]]
   {:fx [[:show-alert error]]}))

(re-frame/reg-fx
 ::deserialize
 (fn [file-desc]
   (.. (js/Promise. (fn [resolve]
                      (-> (. js/JSON (parse (:content file-desc)))
                          (js->clj :keywordize-keys true)
                          :project
                          resolve)))
       (then project-serialization/deserialize+)
       (then (fn [res]
               (re-frame/dispatch [::deserialize-success (:sprite res)])))
       (catch (fn []
                (re-frame/dispatch [::deserialize-error "invalid format of file"]))))))
