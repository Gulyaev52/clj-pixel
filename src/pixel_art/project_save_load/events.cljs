(ns pixel-art.project-save-load.events
  (:require
   [pixel-art.project-save-load.backup :as backup]
   [pixel-art.project-save-load.sprite-serialization :as sprite-serialization]
   [pixel-art.tool.utils :refer [check-unsaved-changes-exist
                                 mark-unsaved-changes-saved]]
   [re-frame.core :as re-frame]))

;; todo: rename this feature

(def file-ext "json")

(re-frame/reg-event-fx
 ::save-as-file
 (fn [{:keys [db]}]
   (let [title (-> db :sprite :title)
         file-desc {:file-name (str title "." file-ext)
                    :content (-> {:version "1"
                                  :project {:sprite (sprite-serialization/serialize (:sprite db))}}
                                 clj->js
                                 (#(. js/JSON (stringify %))))
                    :content-type :json}]
     {:db (mark-unsaved-changes-saved db)
      :fx [[:download-file file-desc]]})))

(re-frame/reg-event-fx
 ::load-from-file
 (fn [_ [_ file-desc]]
   {:fx [[::deserialize-from-file file-desc]]}))

(re-frame/reg-event-fx
 ::deserialize-success
 (fn [{:keys [db]} [_ sprite]]
   {:fx [[:dispatch [:initialize-db
                     (merge {:sprite sprite}
                            (select-keys db [:palettes :primary-color :secondary-color]))]]]}))

(re-frame/reg-event-fx
 ::deserialize-from-file-error
 (fn [_ [_ error]]
   {:fx [[:show-alert error]]}))

(re-frame/reg-fx
 ::deserialize-from-file
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
                (re-frame/dispatch [::deserialize-from-file-error "invalid format of file"]))))))

(defn- save-backup-if-need [db on-success on-failure]
  (if (check-unsaved-changes-exist db)
    {:db (-> db
             (mark-unsaved-changes-saved))
     :fx [[::save-backup {:backup (select-keys db [:sprite])
                          :on-success on-success
                          :on-failure on-failure}]]}
    {}))

(re-frame/reg-event-fx
 ::save-in-browser
 (fn [{:keys [db]}]
   (save-backup-if-need db
                        [:show-notification {:type :success
                                             :message "Successfully saved!"}]
                        [:show-notification {:type :error
                                             :message "Something wrong!"}])))

(re-frame/reg-event-fx
 ::save-backup-if-need
 (fn [{:keys [db]}]
   (save-backup-if-need db nil nil)))

(re-frame/reg-fx
 ::save-backup
 (fn [{:keys [backup on-success on-failure]}]
   (. (backup/put-backup+ @backup/!db backup)
      (then #(when on-success
               (re-frame/dispatch on-success))
            #(when on-failure
               (re-frame/dispatch on-failure))))))
