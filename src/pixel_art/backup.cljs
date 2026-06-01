(ns pixel-art.backup
  (:require
   [pixel-art.db.utils :as db.utils]
   [pixel-art.project-save-load.sprite-serialization :as sprite-serialization]
   [re-frame.core :as re-frame]))

(defonce !db (atom nil))

(defn- open-object-store [db]
  (.. db (transaction #js ["pixel"] "readwrite") (objectStore "pixel")))

(defn- request->promise [request]
  (js/Promise. (fn [resolve reject]
                 (set! (. request -onsuccess) resolve)
                 (set! (. request -onerror) reject))))

(defn init-db+ []
  (if-not @!db
    (js/Promise.
     (fn [resolve reject]
       (let [open-request (.. js/window -indexedDB (open "pixel-database" 1))]
         (set! (. open-request -onupgradeneeded)
               (fn [event]
                 (let [db (.. event -target -result)
                       _ (.. db (createObjectStore "pixel" #js {"keyPath" "id"}))]
                   (reset! !db db))))

         (set! (. open-request -onsuccess) (fn [event]
                                             (let [db (.. event -target -result)]
                                               (reset! !db db)
                                               (resolve db))))

         (set! (. open-request -onerror) (fn []
                                           (. js/console (error "Error" (.-error open-request)))
                                           (reject (.-error open-request)))))))
    (. js/Promise (resolve @!db))))

(defn get-backup+ [db]
  (let [store (open-object-store db)]
    (.. (request->promise (. store (get "backup")))
        (then (fn [event]
                (when-let [backup (-> (.. event -target -result)
                                      (js->clj :keywordize-keys true)
                                      :backup)]
                  (. (sprite-serialization/deserialize+ (:sprite backup))
                     (then (fn [sprite]
                             (assoc backup :sprite sprite))))))))))

(defn put-backup+ [db backup]
  ;; update or create a new backup if not exists
  (let [store (open-object-store db)
        record (-> {:id "backup"
                    :backup (-> backup
                                (update :sprite sprite-serialization/serialize))}
                   clj->js)]
    (request->promise (. store (put record)))))

;; move events and fx to another ns?

(re-frame/reg-event-fx
 ::save-backup
 (fn [{:keys [db]} [_ success-message]]
   {:db db
    :fx [[::save-backup {:backup (select-keys db [:sprite])
                         :success-message success-message}]]}))

(re-frame/reg-event-fx
 ::save-backup-if-need
 (fn [{:keys [db]} [_ success-message]]
   (if (db.utils/check-unsaved-changes-exist db)
     {:db db
      :fx [[::save-backup {:backup (select-keys db [:sprite])
                           :success-message success-message}]]}
     {:db db})))

(re-frame/reg-fx
 ::save-backup
 (fn [{:keys [backup success-message failure-message]}]
   (. (put-backup+ @!db backup)
      (then #(when success-message
               (re-frame/dispatch [::handle-save-backup-result :success success-message]))
            #(when failure-message
               (re-frame/dispatch [:show-notification {:type :error
                                                       :message failure-message}]))))))

(re-frame/reg-event-fx
 ::handle-save-backup-result
 (fn [{:keys [db]} [_ type message]]
   {:db (if (= type :success) (db.utils/mark-unsaved-changes-saved db) db)
    :fx [[:show-notification {:type type
                              :message message}]]}))
