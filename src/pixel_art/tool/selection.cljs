(ns pixel-art.tool.selection
  (:require
   [pixel-art.canvas :as canvas]
   [pixel-art.model.color :as color]
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                 commit-changes-and-init-tool2
                                 with-highlight-cel-under-cursor]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]))

(defn- remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color color/transparent-color-int)))
       (into {})))

(defn- init [type] {:type type :state {:mode :select
                                       :user-is-making-selection false}})

(defn- commit-moved-selection [db]
  (let [type (-> db :tool :type)]
    (commit-changes-and-init-tool2 db (:preview db) (init type))))

(defn- move-selection [tool initial-mouse-down-pos event]
  (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
        {:keys [initial-selection-image selection-image pasted?]} (:state tool)

        deleted-initial-selection (when (not pasted?)
                                    (update-vals initial-selection-image (fn [_] color/transparent-color-int)))
        moved-selection-image (-> selection-image
                                  (update-keys #(merge-with + % offset-pos)))
        changes (merge deleted-initial-selection
                       (remove-transparent-colors moved-selection-image))]
    {:changes changes ;; todo: а это точно нужно?
     :moved-selection-image moved-selection-image}))

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
              (if (not get-selection-only-on-mouse-down)
                (let [selection-image (get-selection db event)
                      updated-tool (assoc-in (:tool db) [:state :user-is-making-selection] true) ;; когда меняется мод с move-selection -> select, то происходит up event и снова создаётся селектион
                      ]
                  {:db (assoc db :tool updated-tool)
                   :fx [[:clear-visual-effects]
                        [:highlight-selection selection-image]]})
                {:db db}))
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
            (let [{:keys [changes]} (move-selection tool initial-mouse-down-pos event)]
              {:db (assoc-in db [:tool :state :changes] changes)
               :fx [[:clear-visual-effects]
                    [:clear-preview]
                    [:draw-preview changes]]}))
          :mouse-up
          (fn [db event]
            (let [{:keys [changes moved-selection-image]} (move-selection tool initial-mouse-down-pos event)
                  updated-tool (-> tool
                                   (assoc-in [:state :selection-image] moved-selection-image)
                                   (assoc-in [:state :changes] changes))]
              {:db (assoc db :tool updated-tool)
               :fx [[:clear-preview]
                    [:draw-preview changes]
                    [:highlight-selection moved-selection-image]]}))})))})

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
                                    (update-vals initial-selection-image (fn [_] color/transparent-color-int)))]
    (commit-changes-and-init-tool2 db
                                   deleted-initial-selection
                                   {:type tool-type :state {:mode :select}})))

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
                             :pasted? true}}]
       (-> db
           commit-moved-selection
           (assoc-in [:db :tool] new-tool)
           (update :fx #(concat % [[:clear-preview]
                                   [:draw-preview changes]
                                   [:clear-visual-effects]
                                   [:highlight-selection selection-image]]))))
     {:db db})))

(re-frame/reg-fx
 :highlight-selection
 (fn [selection]
   (let [size (-> @db/app-db :sprite :size)
         canvas (. js/document (getElementById "visual-effects"))]
     (->> selection
          (filter (fn [[pos]] (geometry/valid-point? pos size)))
          (map (fn [[pos color]] [pos (color/get-highlight-color color)]))
          (#(canvas/update-image-data canvas %))))))
