(ns pixel-art.tool.selection
  (:require
   [pixel-art.model.color :as color]
   [pixel-art.model.preview :as preview]
   [pixel-art.tool.utils :refer [commit-preview-and-init-tool get-current-cel
                                 get-empty-visual-effects
                                 get-preview-from-current-cel
                                 with-highlight-cel-under-cursor]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]))

(defn- remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color color/transparent-color-int)))
       (into {})))

(defn- init [type] {:type type :state {:mode :select
                                       :user-is-making-selection false}})

(defn- highlight-selection [db selection]
  (let [size (-> db :sprite :size)
        current-pixels (or (:preview db) (:pixels (get-current-cel db)))
        visual-effects (get-empty-visual-effects db)]
    (doseq [[pos] selection]
      (when-let [idx (geometry/pos->idx (:x pos) (:y pos) size)]
        (aset visual-effects idx
              (color/get-highlight-color (nth current-pixels idx)))))
    (-> db
        (assoc :visual-effects visual-effects))))

(defn- commit-moved-selection [db]
  (let [type (-> db :tool :type)]
    (commit-preview-and-init-tool db (:preview db) (init type))))

(defn- move-selection [preview tool initial-mouse-down-pos event]
  (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
        {:keys [initial-selection-image selection-image pasted?]} (:state tool)

        deleted-initial-selection (when (not pasted?)
                                    (update-vals initial-selection-image (fn [_] color/transparent-color-int)))
        moved-selection-image (-> selection-image
                                  (update-keys #(merge-with + % offset-pos)))
        changes (merge deleted-initial-selection
                       (remove-transparent-colors moved-selection-image))]
    (doseq [[{:keys [x y]} color] changes]
      (preview/set-color! preview x y color))
    {:changes changes ;; todo: а это точно нужно?
     :moved-selection-image moved-selection-image
     :preview preview}))

(defn make [{:keys [type get-selection get-selection-only-on-mouse-down]}]
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
              (let [selection-image (get-selection db event)
                    updated-tool (assoc-in (:tool db) [:state :user-is-making-selection] true) ;; без этого когда меняется мод с move-selection -> select, то происходит up event и снова создаётся селектион
                    ]
                {:db (-> db
                         (assoc :tool updated-tool)
                         (highlight-selection selection-image))}))
            :mouse-up
            (fn [db event]
              (if (-> db :tool :state :user-is-making-selection)
                (let [selection-image (get-selection db event)
                      tool (assoc tool :state {:mode :move-selection
                                               :initial-selection-image selection-image
                                               :selection-image selection-image
                                               :changes []})]
                  {:db (assoc db :tool tool)})
                {:db db}))})

         :move-selection
         {:mouse-down
          (fn [db event]
            (if (not (contains? (-> tool :state :selection-image) (:pos event)))
              (commit-moved-selection db)
              {:db db}))
          :mouse-down-and-move
          (fn [db event]
            (let [{:keys [changes preview]} (move-selection (get-preview-from-current-cel db) tool initial-mouse-down-pos event)]
              {:db (-> db
                       (assoc-in [:tool :state :changes] changes)
                       (assoc :preview preview)
                       (assoc :visual-effects nil))}))
          :mouse-up
          (fn [db event]
            (let [{:keys [changes moved-selection-image preview]} (move-selection (get-preview-from-current-cel db) tool initial-mouse-down-pos event)
                  updated-tool (-> tool
                                   (assoc-in [:state :selection-image] moved-selection-image)
                                   (assoc-in [:state :changes] changes))]
              {:db (-> db
                       (assoc :tool updated-tool)
                       (assoc :preview preview)
                       (highlight-selection moved-selection-image))}))})))})

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
        deleted-initial-selection (if pasted?
                                    {}
                                    (update-vals initial-selection-image (fn [_] color/transparent-color-int)))
        preview (get-preview-from-current-cel db)]
    (doseq [[{:keys [x y]} color] deleted-initial-selection]
      (preview/set-color! preview x y color))
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
           changes (remove-transparent-colors selection-image)
           new-tool {:type tool-type
                     :state {:mode :move-selection
                             :initial-selection-image selection-image
                             :selection-image selection-image
                             :changes changes
                             :pasted? true}}
           preview (get-preview-from-current-cel db)]
       (doseq [[{:keys [x y]} color] changes]
         (preview/set-color! preview x y color))
       (-> db
           commit-moved-selection
           (assoc-in [:db :tool] new-tool)
           (update :db #(-> %
                            (assoc :preview preview)
                            (highlight-selection selection-image)))))
     {:db db})))
