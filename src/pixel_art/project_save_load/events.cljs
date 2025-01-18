(ns pixel-art.project-save-load.events
  (:require [re-frame.core :as re-frame]
            [pixel-art.sprite-serialization :as sprite-serialization]))

(def file-ext "json")

(re-frame/reg-event-fx
 ::save-as-file
 (fn [{:keys [db]}]
   (let [file-desc {:file-name (str "pixel-project." file-ext)
                    :content (-> {:version "1"
                                  :project {:sprite (sprite-serialization/serialize (:sprite db))}}
                                 clj->js
                                 (#(. js/JSON (stringify %))))
                    :content-type :json}]
     {:fx [[:download-file file-desc]]})))

(re-frame/reg-event-fx
 ::load-from-file
 (fn [_ [_ file-desc]]
   {:fx [[::deserialize file-desc]]}))

(re-frame/reg-event-fx
 ::deserialize-success
 (fn [{:keys [db]} [_ sprite]]
   {:fx [[:dispatch [:initialize-db
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
       (then (fn [project]
               (. (sprite-serialization/deserialize+ (:sprite project))
                  (then (fn [sprite]
                          (assoc project :sprite sprite))))))
       (then (fn [res]
               (re-frame/dispatch [::deserialize-success (:sprite res)])))
       (catch (fn []
                (re-frame/dispatch [::deserialize-error "invalid format of file"]))))))
