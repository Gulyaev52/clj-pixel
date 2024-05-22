(ns pixel-art.palette
  (:require [pixel-art.palette.gimp-file :as gimp-file]
            [pixel-art.tool.utils :refer [get-active-color-type]]
            [pixel-art.utils.coll :as coll]
            [pixel-art.utils.interceptor :refer [on-changes]]
            [re-frame.core :as re-frame]
            [sc.api :as api]))

;; todo
;; сохранять в localstorage
;; primary/secondary colors. правое или левое нажатие мыши. palette. eraser; swap-colors
;;     color-picker-with-button. primary-color
;; color picker
;; добавлять в историю?
;; move color
;; удаление палетки. 1 осталась
;; default палетка должна быть особенной?

(defn get-current-palette [db]
  (let [{:keys [selected-palette-idx palettes]} db]
    (nth palettes selected-palette-idx)))

(defn init [db]
  (merge db {:selected-palette-idx 0
             :palettes [{:name "default"
                         :colors ["black" "red" "green" "blue" "yellow" "gray" "purple"]} ;; todo: use set?
                        ]}))

(re-frame/reg-global-interceptor
 (on-changes
  :save-palettes-in-local-storage
  #(-> % :sprite)
  (fn [{:keys [db old new]}]
    {:db db
     :fx []})))

(re-frame/reg-event-fx
 ::select-palette
 (fn [{:keys [db]} [_ idx]]
   {:db (assoc db :selected-palette-idx idx)}))

(re-frame/reg-event-fx
 ::select-color
 (fn [{:keys [db]} [_ idx right-mouse-button]]
   (let [new-selected-color (-> (get-current-palette db)
                                :colors
                                (nth idx))]
     {:db (assoc db (get-active-color-type right-mouse-button) new-selected-color)})))

(re-frame/reg-event-fx
 ::remove-selected-palette
 (fn [{:keys [db]} [_]]
   {:db (-> db
            (update :palettes #(coll/removev (:selected-palette-idx db) %))
            (assoc :selected-palette-idx 0))}))

(re-frame/reg-event-fx
 ::rename-selected-palette
 (fn [{:keys [db]} [_ name]]
   {:db (-> db
            (assoc-in [:palettes (:selected-palette-idx db) :name] name))}))

(re-frame/reg-event-fx
 ::create-palette
 (fn [{:keys [db]} [_ name]]
   {:db (-> db
            (update :palettes #(conj % {:name name :colors []}))
            (assoc :selected-palette-idx (count (:palettes db)))
            (dissoc :palette-manager))}))

(re-frame/reg-event-fx
 ::remove-color
 (fn [{:keys [db]} [_ idx]]
   {:db (update-in db [:palettes (:selected-palette-idx db) :colors] #(coll/removev idx %))}))

(re-frame/reg-event-fx
 ::add-color
 (fn [{:keys [db]} [_ color]]
   {:db (-> db
            (update-in [:palettes (:selected-palette-idx db) :colors] #(-> %
                                                                           (conj color)
                                                                           distinct
                                                                           vec))
            (assoc :primary-color color))}))

(re-frame/reg-event-fx
 ::load-palette
 (fn [{:keys [db]} [_ {:keys [file-name content]}]]
   (if-let [palette (:ok (gimp-file/parse-content content))]
     {:db (-> db
              (update :palettes #(conj % palette))
              (assoc :selected-palette-idx (count (:palettes db))))}
     {:db db
      :fx [[:show-alert "invalid file content"]]})))

(re-frame/reg-event-fx
 ::download-palette
 (fn [{:keys [db]}]
   {:db db
    :fx [[:download-file (->> (get-current-palette db)
                              gimp-file/palette->file-desc)]]}))

(re-frame/reg-fx
 :show-alert
 (fn [message]
   (js/alert message)))

(re-frame/reg-fx
 :download-file
 (fn [{:keys [file-name content]}]
   (let [data-blob (js/Blob. #js [content] #js {:type "application/json"})
         link (.createElement js/document "a")]
     (set! (.-href link) (.createObjectURL js/URL data-blob))
     (.setAttribute link "download" file-name)
     (.appendChild (.-body js/document) link)
     (.click link)
     (.removeChild (.-body js/document) link))))
