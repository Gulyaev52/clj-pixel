(ns pixel-art.palette
  (:require [pixel-art.local-storage :as local-storage]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.palette.gimp-file :as gimp-file]
            [pixel-art.tool.utils :refer [get-current-color-type]]
            [pixel-art.utils.coll :as coll]
            [pixel-art.utils.interceptor :refer [on-changes]]
            [re-frame.core :as re-frame]
            [sc.api]))

;; todo
;; добавлять в историю?
;; move color
;; default палетка должна быть особенной?

(defn deletable-palette? [palettes]
  (> (count palettes) 1))

(defn get-current-palette-idx [db]
  (coll/find-first-idx :current (:palettes db)))

(defn get-current-palette [db]
  (nth (:palettes db) (get-current-palette-idx db)))

(def local-storage-key :palettes)
(defn init [palettes] palettes)

(re-frame/reg-global-interceptor
 (on-changes
  :save-palettes-in-local-storage-on-palettes-change
  #(:palettes %)
  (fn [{:keys [db]}]
    {:db db
     :fx [[::local-storage/set-item {:key local-storage-key
                                     :value (:palettes db)}]]})))

(re-frame/reg-event-fx
 ::select-palette
 (fn [{:keys [db]} [_ new-current-idx]]
   (let [palettes (vec (map-indexed (fn [p-idx p] (assoc p :current (= new-current-idx p-idx)))
                                    (:palettes db)))]
     {:db (assoc db :palettes palettes)})))

(re-frame/reg-event-fx
 ::select-color
 (fn [{:keys [db]} [_ idx right-mouse-button]]
   (let [new-current-color (-> (get-current-palette db)
                               :colors
                               (nth idx))]
     {:db (assoc db (get-current-color-type right-mouse-button) new-current-color)})))

(re-frame/reg-event-fx
 ::remove-selected-palette
 (fn [{:keys [db]} [_]]
   (if (deletable-palette? (:palettes db))
     (let [palettes (->> (:palettes db)
                         (coll/removev (get-current-palette-idx db))
                         (#(assoc-in % [0 :current] true)))]
       {:db (assoc db :palettes palettes)})
     {:db db})))

(re-frame/reg-event-fx
 ::rename-selected-palette
 (fn [{:keys [db]} [_ name]]
   {:db (-> db
            (assoc-in [:palettes (get-current-palette-idx db) :name] name))}))

(re-frame/reg-event-fx
 ::create-palette
 (fn [{:keys [db]} [_ name]]
   (let [palettes (conj (mapv #(assoc % :current false) (:palettes db))
                        {:name name :colors [] :current true})]
     {:db (assoc db :palettes palettes)})))

(re-frame/reg-event-fx
 ::remove-color
 (fn [{:keys [db]} [_ idx]]
   {:db (update-in db [:palettes (get-current-palette-idx db) :colors] #(coll/removev idx %))}))

(re-frame/reg-event-fx
 ::add-color
 (fn [{:keys [db]} [_ color]]
   {:db (-> db
            (update-in [:palettes (get-current-palette-idx db) :colors] #(-> (conj % color)
                                                                             distinct
                                                                             vec)))}))

(re-frame/reg-event-fx
 ::add-colors-from-frame
 (fn [{:keys [db]}]
   (let [sprite (:sprite db)
         cels (sprite/get-frame-cels (sprite/get-current-frame-idx sprite) sprite)
         colors (->> cels
                     (mapcat :pixels)
                     distinct
                     (remove #(= transparent-color %)))]
     {:db (-> db
              (update-in [:palettes (get-current-palette-idx db) :colors] #(->> (concat % colors)
                                                                                distinct
                                                                                vec)))})))

(re-frame/reg-event-fx
 ::load-palette
 (fn [{:keys [db]} [_ file-desc]]
   (if-let [palette (:ok (gimp-file/parse-content (:content file-desc)))]
     (let [palettes (->> (:palettes db)
                         (mapv #(assoc % :current false))
                         (#(conj % (assoc palette :current true))))]
       {:db (assoc db :palettes palettes)})
     {:db db
      :fx [[:show-alert "invalid file content"]]})))

(re-frame/reg-event-fx
 ::download-palette
 (fn [{:keys [db]}]
   {:db db
    :fx [[:download-file (->> (get-current-palette db)
                              gimp-file/palette->file-desc)]]}))
