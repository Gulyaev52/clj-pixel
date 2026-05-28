(ns pixel-art.events
  (:require
   [pixel-art.project-save-load.backup :as backup]
   [pixel-art.db :as db]
   [pixel-art.drawing.events]
   [pixel-art.keyboard-shortcuts :as keyboard-shortcuts]
   [pixel-art.project-save-load.events :as project-save-load]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.re-pressed.core :as rp]
   [pixel-art.tool.core :as tool]
   [pixel-art.tool.utils :refer [check-unsaved-changes-exist
                                 commit-preview-and-init-tool]]
   [pixel-art.utils.fx]
   [pixel-art.utils.fx.local-storage :as local-storage]
   [pixel-art.utils.interceptor :refer [on-paths-change]]
   [re-frame.core :as re-frame]
   [re-frame.db]
   [sc.api]))

;; global events

(def saved-settings-local-storage-key :saved-settings)

(re-frame/reg-event-fx
 ::start-app
 (fn [_ [_ initial-app-data-for-test]]
   (if initial-app-data-for-test
     {:db {:initial-loading true}
      :fx [[:dispatch [:initialize-db initial-app-data-for-test]]]}
     {:db {:initial-loading true}
      :fx [[::load-initial-data]]})))

(re-frame/reg-fx
 ::load-initial-data
 (fn []
   (.. (backup/init-db+)
       (then backup/get-backup+)
       (then (fn [backup]
               (re-frame/dispatch [:initialize-db backup]))) ;; todo: fix
       (catch (fn []
                (re-frame/dispatch [:initialize-db]))))))

(def dispatch-set-keydown-rules
  (let [convert-shortcut-keys #(-> %
                                   (assoc :keyCode (keyboard-shortcuts/key->code (:key %)))
                                   (dissoc :key))]
    [:dispatch [::rp/set-keydown-rules
                {:event-keys (->> keyboard-shortcuts/shortcuts-by-types
                                  vals
                                  flatten
                                  ;; Order matters, and the first matching key combination will consume the event. So for example, if you want to listen for both forward arrow ({:keyCode 37}) and control + forward arrow ({:keyCode 37 :ctrlKey true}), then you must put the combination before the singleton. 
                                  (sort-by (fn [{:keys [keys]}] (not (some :ctrlKey keys))))
                                  (map (fn [{:keys [action keys]}]
                                         (into [action] (->> keys
                                                             (map convert-shortcut-keys)
                                                             (map vector))))))
                 :prevent-default-keys (->> keyboard-shortcuts/shortcuts-by-types
                                            vals
                                            flatten
                                            (filter :prevent-default-keys)
                                            (mapcat (fn [{:keys [keys]}] keys))
                                            (map convert-shortcut-keys))}]]))

(re-frame/reg-cofx
 :viewport-size
 (fn [coeffects _]
   (let [viewport-rect (.. js/document (getElementById "canvas-viewport") (getBoundingClientRect))
         viewport-size {:width (. viewport-rect -width) :height (. viewport-rect -height)}]
     (assoc coeffects :viewport-size viewport-size))))

(re-frame/reg-event-fx
 :initialize-db
 [(re-frame/inject-cofx ::local-storage/get-item saved-settings-local-storage-key) (re-frame/inject-cofx :viewport-size)]
 (fn [{:keys [viewport-size] :as cofx} [_ initial-app-data]]
   (let [saved-data (into {} (filter (fn [[_ v]] (some? v)) (get cofx saved-settings-local-storage-key)))
         initial-db (if initial-app-data
                      (db/get-db (merge initial-app-data saved-data) viewport-size)
                      (db/get-db (merge (assoc project-settings/default-palettes-and-current-colors
                                               :sprite (project-settings/create-empty-sprite "Untitled" {:width 4 :height 4})
                                               :new-project-modal-opened false)
                                        saved-data)
                                 viewport-size))]
     {:db initial-db
      :fx [[:dispatch [::rp/add-keyboard-event-listener "keydown"]]
           dispatch-set-keydown-rules
           [:dispatch-interval {:dispatch [::project-save-load/save-backup-if-need]
                                :id :backup
                                :ms 60000 ;; 1 min
                                }]
           [::show-warning-when-leave-with-unsaved-changes]
           [::register-global-interceptors]]})))

(re-frame/reg-fx
 ::register-global-interceptors
 (fn []
   (re-frame/reg-global-interceptor
    (on-paths-change
     :saved-settings
     [:primary-color :secondary-color :palettes :pixels-grid-enabled]
     (fn [{:keys [db fields]}]
       {:db db
        :fx [[::local-storage/set-item {:key saved-settings-local-storage-key
                                        :value fields}]]})))))

(defn show-warning-when-leave-with-unsaved-changes [e]
  (when (check-unsaved-changes-exist @re-frame.db/app-db)
    (let [confirmMessage "Your current sprite has unsaved changes. Are you sure you want to quit?"]
      (when-let [e (or e (. js/window -event))]
        (set! (. e -returnValue) confirmMessage)
        confirmMessage))))

(re-frame/reg-fx
 ::show-warning-when-leave-with-unsaved-changes
 (fn []
   (. js/window (removeEventListener "beforeunload" show-warning-when-leave-with-unsaved-changes))
   (. js/window (addEventListener "beforeunload" show-warning-when-leave-with-unsaved-changes))))

;; todo: move bellow actions?

(re-frame/reg-event-fx
 ::select-tool
 (fn [{:keys [db]} [_ tool-type]]
   (let [tool (tool/init tool-type)]
     (commit-preview-and-init-tool db (:preview db) tool))))

(re-frame/reg-event-fx
 ::change-tool-option ;; todo: set
 (fn [{:keys [db]} [_ field value]]
   (let [tool-type (-> db :tool :type)]
     {:db (assoc-in db [:tools-options tool-type field] value)})))

(re-frame/reg-event-fx
 ::swap-current-colors
 (fn [{:keys [db]}]
   {:db (assoc db
               :primary-color (:secondary-color db)
               :secondary-color (:primary-color db))}))

(re-frame/reg-event-fx
 ::set-current-color
 (fn [{:keys [db]} [_ type color]]
   {:db (assoc db type color)}))

(re-frame/reg-event-fx
 ::set-sprite-title
 (fn [{:keys [db]} [_ title]]
   {:db (assoc-in db [:sprite :title] title)}))
