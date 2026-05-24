(ns pixel-art.utils.fx.local-storage
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::set-item
 (fn [{:keys [db]} [_ params]]
   {:db db
    :fx [[::set-item params]]}))

(re-frame/reg-fx
 ::set-item
 (fn [{:keys [key value]}]
   (. js/localStorage (setItem (name key) (. js/JSON (stringify (clj->js value)))))))

(re-frame/reg-cofx
 ::get-item
 (fn [coeffects key]
   (let [item (when-let [item (. js/localStorage (getItem (name key)))]
                (js->clj (. js/JSON (parse item)) :keywordize-keys true))]
     (assoc coeffects key item))))

(re-frame/reg-cofx
 ::all
 (fn [coeffects _]
   (assoc coeffects
          :local-storage
          (into {} (js->clj (. js/Object (entries js/localStorage)))))))