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

(defn- init [type] {:type type :state {:mode :select
                                       :user-is-making-selection false}})

(defn- highlight-selection [db get-selection]
  (let [{:keys [width height]} (-> db :sprite :size)
        current-pixels (or (:preview db) (:pixels (get-current-cel db)))
        visual-effects (get-empty-visual-effects db)]
    (get-selection (fn [x y]
                     (when-let [idx (geometry/pos->idx x y width height)]
                       (aset visual-effects idx
                             (color/get-highlight-color (aget current-pixels idx))))))
    (-> db
        (assoc :visual-effects visual-effects))))

(defn- commit-moved-selection [db]
  (let [type (-> db :tool :type)]
    (commit-preview-and-init-tool db (:preview db) (init type))))

#_(def initial-selection-and-preview! (atom nil))
#_(defn cut-selection-from-preview [initial-selection preview]
    (let [[prev-initial-selection cached-preview] @initial-selection-and-preview!]
      (if (= prev-initial-selection initial-selection)
        (do (println "cache")
            (. cached-preview slice))
        (do
          (doseq [tuple initial-selection]
            (preview/set-color! preview (aget tuple 0 0) (aget tuple 0 1) color/transparent-color-int))
          (reset! initial-selection-and-preview! [initial-selection (. preview slice)])
          (println "rev")
          preview))))

(defn cut-initial-selection-from-preview-if-need [size preview initial-selection pasted?]
  (if (not pasted?)
    (do
      (doseq [tuple initial-selection]
        (preview/set-color! preview (aget tuple 0 0) (aget tuple 0 1) color/transparent-color-int))
      preview)
    preview))

(defn- move-selection [db preview tool initial-mouse-down-pos event]
  (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
        offset-x (:x offset-pos)
        offset-y (:y offset-pos)
        {:keys [initial-selection-image pasted?]} (:state tool)
        preview-with-deletion-initial-selection (or (when-let [p (:preview-with-deletion-initial-selection (:state tool))]
                                                      p)
                                                    (cut-initial-selection-from-preview-if-need (-> db :sprite :size) preview initial-selection-image pasted?))
        preview (preview/create (-> db :sprite :size) preview-with-deletion-initial-selection)]
    (doseq [tuple initial-selection-image]
      (when (not= (aget tuple 1) color/transparent-color-int)
        (preview/set-color! preview (+ (aget tuple 0 0) offset-x) (+ (aget tuple 0 1) offset-y) (aget tuple 1))))
    {:preview preview
     :preview-with-deletion-initial-selection preview-with-deletion-initial-selection}))

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
                (let [updated-tool (assoc-in (:tool db) [:state :user-is-making-selection] true) ;; без этого когда меняется мод с move-selection -> select, то происходит up event и снова создаётся селектион
                      ]
                  {:db (-> db
                           (assoc :tool updated-tool)
                           (highlight-selection #(get-selection % db event)))})
                {:db db}))
            :mouse-up
            (fn [db event]
              (if (-> db :tool :state :user-is-making-selection)
                (let [selection-image-js #js []
                      current-cel (get-current-cel db)
                      _ (get-selection (fn [x y] (. selection-image-js (push #js [#js [x y] (cel/get-pixel x y current-cel)]))) db event)
                      selection-image (vec selection-image-js)
                      tool (assoc tool :state {:mode :move-selection
                                               :initial-selection-image selection-image
                                               :selection-image selection-image
                                               :changes []})]
                  {:db (assoc db :tool tool)})
                {:db db}))})

         :move-selection
         {:mouse-down
          (fn [db event]
            (let [points (set (map (fn [[point]] (vec point)) (-> tool :state :selection-image)))]
              (if (not (contains? points [(:x (:pos event)) (:y (:pos event))]))
                (commit-moved-selection db)
                {:db db})))
          :mouse-down-and-move
          (fn [db event]
            (let [{:keys [preview-with-deletion-initial-selection preview]} (move-selection db (get-preview-from-current-cel db) tool initial-mouse-down-pos event)]
              {:db (-> db
                       (assoc-in [:tool :state :preview-with-deletion-initial-selection] preview-with-deletion-initial-selection)
                       (assoc :preview preview)
                       (assoc :visual-effects nil))}))
          :mouse-up
          (fn [db event]
            (let [{:keys [preview-with-deletion-initial-selection preview]} (move-selection db (get-preview-from-current-cel db) tool initial-mouse-down-pos event)]
              {:db (-> db
                       (assoc :preview preview)
                       (assoc-in [:tool :state :preview-with-deletion-initial-selection] preview-with-deletion-initial-selection)
                       #_(highlight-selection moved-selection-image))}))})))})

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
           changes selection-image
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
