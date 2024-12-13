(ns pixel-art.backup
  (:require
   [pixel-art.sprite-serialization :as sprite-serialization]
   [pixel-art.utils.interceptor :refer [on-paths-change]]
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
                   (reset! !db db)
                   (resolve db))))

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

;; todo: move to init
(re-frame/reg-global-interceptor
 (on-paths-change
  :backup
  [:primary-color :secondary-color :palettes :sprite]
  (fn [{:keys [db fields]}]
    {:db db
     :fx [[:dispatch-debounce {:key :backup
                               :event [::backup fields]
                               :delay 6000}]]})))

(re-frame/reg-event-fx
 ::backup
 (fn [_ [_ backup]]
   {:fx [[::backup backup]]}))

(re-frame/reg-fx
 ::backup
 (fn [backup]
   (put-backup+ @!db backup)))
