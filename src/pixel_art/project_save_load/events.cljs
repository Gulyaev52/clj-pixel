(ns pixel-art.project-save-load.events
  (:require
   [pixel-art.project-save-load.backup :as backup]
   [pixel-art.project-save-load.sprite-serialization :as sprite-serialization]
   [pixel-art.db :as db]
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
     {:db (db/mark-unsaved-changes-saved db)
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

(re-frame/reg-event-fx
 ::save-in-browser
 (fn [{:keys [db]}]
   {:db db
    :fx [[::save-backup {:backup (select-keys db [:sprite])
                         :success-message "Successfully saved!"
                         :failure-message "Something wrong!"}]]}))

(re-frame/reg-event-fx
 ::save-backup-if-need
 (fn [{:keys [db]}]
   (if (db/check-unsaved-changes-exist db)
     {:db db
      :fx [[::save-backup {:backup (select-keys db [:sprite])
                           :success-message "Auto-backup"}]]}
     {})))

(re-frame/reg-event-fx
 ::handle-save-backup-result
 (fn [{:keys [db]} [_ type message]]
   {:db (if (= type :success) (db/mark-unsaved-changes-saved db) db)
    :fx [[:show-notification {:type type
                              :message message}]]}))

(re-frame/reg-fx
 ::save-backup
 (fn [{:keys [backup success-message failure-message]}]
   (. (backup/put-backup+ @backup/!db backup)
      (then (when success-message
              (re-frame/dispatch [::handle-save-backup-result :success success-message]))
            #(when failure-message
               (re-frame/dispatch [::handle-save-backup-result :error success-message]))))))
