(ns pixel-art.events
  (:require
   [pixel-art.backup :as backup]
   [pixel-art.db :as db]
   [pixel-art.drawing.events]
   [pixel-art.keyboard-shortcuts :as keyboard-shortcuts]
   [pixel-art.project-save-load.events]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.re-pressed.core :as rp]
   [pixel-art.tool.core :as tool]
   [pixel-art.tool.utils :refer [commit-preview-and-init-tool]]
   [pixel-art.utils.fx]
   [re-frame.core :as re-frame]
   [re-frame.db]
   [sc.api]))

;; todo: remove
(add-watch re-frame.db/app-db :def
           (fn [_ _ _ new]
             (def db new)))

(re-frame/reg-event-fx
 ::start-app
 (fn [_ [_ settings]]
   (if settings
     {:db {:initial-loading true}
      :fx [[:dispatch [:initialize-db settings]]]}
     {:db {:initial-loading true}
      :fx [[::load-initial-data]]})))

(re-frame/reg-fx
 ::load-initial-data
 (fn []
   (.. (backup/init-db+)
       (then backup/get-backup+)
       (then (fn [backup]
               (re-frame/dispatch [:initialize-db]))) ;; todo: fix
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
   (let [viewport-rect (.. js/document (getElementById "viewport") (getBoundingClientRect))
         viewport-size {:width (. viewport-rect -width) :height (. viewport-rect -height)}]
     (assoc coeffects :viewport-size viewport-size))))

(re-frame/reg-event-fx
 :initialize-db
 [(re-frame/inject-cofx :viewport-size)]
 (fn [{:keys [viewport-size]} [_ settings]]
   (let [initial-db (if settings
                      (db/get-db settings viewport-size)
                      (db/get-db (assoc project-settings/default-palettes-and-current-colors
                                        :sprite (project-settings/create-empty-sprite {:width 512 :height 512})
                                        :new-project-modal-opened false)
                                 viewport-size))]
     {:db initial-db
      :fx [[:dispatch [::rp/add-keyboard-event-listener "keydown"]]
           dispatch-set-keydown-rules]})))

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
