(ns pixel-art.tool.selection
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.model.preview :as preview]
   [pixel-art.tool.utils :refer [commit-preview-and-init-tool get-current-cel
                                 get-empty-visual-effects
                                 get-preview-from-current-cel
                                 with-highlight-cel-under-cursor]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]
   [sc.api :as api]))

;; использовать idx вместо поинтов?
;; пропускать лишние эвенты
;; в selection-points только текущие точки а в initial-selection-image мапа

(defn- remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color color/transparent-color-int)))
       (into {})))

(defn- init [type] {:type type :state {:mode :select
                                       :user-is-making-selection false}})

(defn- highlight-selection [db selection-points]
  (let [{:keys [width height]} (-> db :sprite :size)
        current-pixels (or (:preview db) (:pixels (get-current-cel db)))
        visual-effects (get-empty-visual-effects db)]
    ;; forEarch is much faster than doseq
    ;; don't use desctruc for pos because it leads to bad performance
    (. selection-points (forEach (fn [pos]
                                   (when-let [idx (geometry/pos->idx (aget pos 0) (aget pos 1) width height)]
                                     (aset visual-effects idx
                                           (color/get-highlight-color (nth current-pixels idx)))))))
    (-> db
        (assoc :visual-effects visual-effects))))

(defn- commit-moved-selection [db]
  (let [type (-> db :tool :type)]
    (commit-preview-and-init-tool db (:preview db) (init type))))

(defn cut-initial-selection-from-preview-if-need [preview initial-selection pasted?] ;; todo: remove
  (if (not pasted?)
    (do
      (doseq [tuple initial-selection]
        (preview/set-color! preview (aget tuple 0 0) (aget tuple 0 1) color/transparent-color-int))
      preview)
    preview))

(defn- move-selection [db tool initial-mouse-down-pos event]
  (let [{:keys [prev-pos]} db
        offset-pos (merge-with - (:pos event) (or prev-pos initial-mouse-down-pos))
        offset-x (:x offset-pos) ;; todo: desctruc
        offset-y (:y offset-pos)
        {:keys [initial-selection-image selection-image pasted?]} (:state tool)
        preview-with-deleted-initial-selection (or (:preview-with-deleted-initial-selection (:state tool))
                                                   (cut-initial-selection-from-preview-if-need (get-preview-from-current-cel db)
                                                                                               initial-selection-image
                                                                                               pasted?))
        preview (preview/create (-> db :sprite :size) preview-with-deleted-initial-selection)]
    (. selection-image
       (forEach (fn [tuple]
                  (let [point (aget tuple 0)
                        color (aget tuple 1)
                        x (aget point 0)
                        y (aget point 1)
                        new-x (aset point 0 (+ x offset-x))
                        new-y (aset point 1 (+ y offset-y))]
                    (when-not (= color color/transparent-color-int)
                      (preview/set-color! preview new-x new-y color))))))
    {:preview-with-deleted-initial-selection preview-with-deleted-initial-selection
     :preview preview
     :selection-image selection-image}))

(defn make [{:keys [type get-selection]}]
  {:type type
   :init (fn [] (init type))
   :get-events-handlers
   (fn [db_]
     (let [{:keys [tool initial-mouse-down-pos]} db_]
       (case (-> db_ :tool :state :mode)
         :select
         (with-highlight-cel-under-cursor
           {:mouse-down-or-mouse-down-and-move
            (fn [db event]
              (let [selection-points (get-selection db event)
                    updated-tool (assoc-in (:tool db) [:state :user-is-making-selection] true) ;; без этого когда меняется мод с move-selection -> select, то происходит up event и снова создаётся селектион
                    ]
                {:db (-> db
                         (assoc :tool updated-tool)
                         (highlight-selection selection-points))}))
            :mouse-up
            (fn [db event]
              (if (-> db :tool :state :user-is-making-selection)
                (let [current-cel (get-current-cel db)
                      selection (. (get-selection db event)
                                   (map (fn [p]
                                          #js [p (cel/get-pixel (aget p 0) (aget p 1) current-cel)])))
                      initial-selection-image (js/structuredClone selection)
                      tool (assoc tool :state {:mode :move-selection
                                               :initial-selection-image initial-selection-image
                                               :selection-image selection})]
                  {:db (assoc db :tool tool)})
                {:db db}))})

         :move-selection
         {:mouse-down
          (fn [db event]
            (let [event-pos (:pos event)]
              (if (not (. (-> tool :state :selection-image) (some (fn [[[x y]]] (and (= x (:x event-pos))
                                                                                     (= y (:y event-pos)))))))
                (commit-moved-selection db)
                {:db db})))
          :mouse-down-and-move
          (fn [db event]
            (let [{:keys [preview-with-deleted-initial-selection selection-image preview]} (move-selection db tool initial-mouse-down-pos event)
                  updated-tool (-> tool
                                   (assoc-in [:state :selection-image] selection-image)
                                   (assoc-in [:state :preview-with-deleted-initial-selection] preview-with-deleted-initial-selection))]
              {:db (-> db
                       (assoc :tool updated-tool)
                       (assoc :preview preview)
                       (assoc :visual-effects nil))}))
          :mouse-up
          (fn [db event]
            (let [{:keys [preview-with-deleted-initial-selection selection-image preview]} (move-selection db tool initial-mouse-down-pos event)
                  selection-image-points (. selection-image (map (fn [[point]] point)))
                  updated-tool (-> tool
                                   (assoc-in [:state :selection-image] selection-image)
                                   (assoc-in [:state :preview-with-deleted-initial-selection] preview-with-deleted-initial-selection))]
              {:db (-> db
                       (assoc :tool updated-tool)
                       (assoc :preview preview)
                       (highlight-selection selection-image-points))}))})))})

(defn copy-selection [db]
  (let [{:keys [selection-image]} (-> db :tool :state)]
    (-> db
        (assoc
         :selection-manager
         {:selection-image selection-image
          :tool-type (-> db :tool :type)}))))

(defn delete-selection-and-commit [db]
  (let [tool-type (-> db :tool :type)
        {:keys [initial-selection-image pasted?]} (-> db :tool :state)
        preview (get-preview-from-current-cel db)]
    (when-not pasted?
      (doseq [[[x y]] initial-selection-image]
        (preview/set-color! preview x y color/transparent-color-int)))
    (commit-preview-and-init-tool db preview {:type tool-type :state {:mode :select}})))

(defn tool-has-selection? [db]
  (-> db :tool :state :selection-image))

(re-frame/reg-event-fx
 ::delete-selection
 (fn [{:keys [db]} _]
   (if (tool-has-selection? db)
     (delete-selection-and-commit db)
     {:db db})))

(re-frame/reg-event-fx
 ::cut-selection
 (fn [{:keys [db]} _]
   (if (tool-has-selection? db)
     (-> (copy-selection db)
         delete-selection-and-commit)
     {:db db})))

(re-frame/reg-event-fx
 ::commit-selection
 (fn [{:keys [db]} _]
   (if (tool-has-selection? db)
     (commit-moved-selection db)
     {:db db})))

(re-frame/reg-event-fx
 ::copy-selection
 (fn [{:keys [db]} _]
   (if (tool-has-selection? db)
     (-> (copy-selection db)
         commit-moved-selection)
     {:db db})))

(re-frame/reg-event-fx
 ::past-selection
 (fn [{:keys [db]} _]
   (if (-> db :selection-manager :selection-image) ;; todo: copied-selection? оно сущ только когда есть копирование
     (let [{:keys [selection-image tool-type]} (:selection-manager db)
           new-tool {:type tool-type
                     :state {:mode :move-selection
                             :initial-selection-image selection-image
                             :selection-image selection-image
                             :pasted? true}}
           preview (get-preview-from-current-cel db)
           selection-image-points (. selection-image (map (fn [[point]] point)))]
       (doseq [[[x y] color] selection-image]
         (when-not (= color color/transparent-color-int)
           (preview/set-color! preview x y color)))
       (-> db
           commit-moved-selection
           (assoc-in [:db :tool] new-tool)
           (update :db #(-> %
                            (assoc :preview preview)
                            (highlight-selection selection-image-points)))))
     {:db db})))
