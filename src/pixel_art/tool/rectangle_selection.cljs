(ns pixel-art.tool.rectangle-selection
  (:require
   [clojure.set]
   [pixel-art.canvas :as canvas]
   [pixel-art.events.event-collector]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool get-current-cel]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]))
;; init
;; todo: используем так как из selection-image удаляются прозр точки(не работает днд) и если проверять вхож
;; todo: а зачем поле state
;; ресайз

(defn init [] {:type :rectangle-selection :state {:mode :select}})

(def options-spec
  [])

(defn remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color color/transparent-color-int)))
       (into {})))

(defn move-selection [tool initial-mouse-down-pos event]
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

(defn- get-rectangle-selection-image [p1 p2 current-cel]
  (->> (geometry/get-rectange-points p1 p2)
       (map (fn [p] [p (cel/get-pixel p current-cel)]))
       (into {})))

(defn commit-moved-selection [db]
  (let [changes (-> db :tool :state :changes)]
    (commit-changes-and-init-tool db changes (init))))

(defn handle-mouse-event [db event]
  (let [{:keys [tool initial-mouse-down-pos user-is-drawing]} db]
    (case (-> tool :state :mode)
      :select
      (cond
        (or (= (:type event) :mouse-down)
            (and (= (:type event) :mouse-move) user-is-drawing))
        {:db (assoc-in db [:tool :state :user-is-making-selection] true)
         :fx [[:clear-visual-effects]
              [:highlight-selection (get-rectangle-selection-image initial-mouse-down-pos
                                                                   (:pos event)
                                                                   (get-current-cel db))]]}

        (and (= :mouse-up (:type event)) (-> tool :state :user-is-making-selection))
        (let [selection-image (get-rectangle-selection-image initial-mouse-down-pos
                                                             (:pos event)
                                                             (get-current-cel db))
              tool (assoc tool :state {:mode :move-selection
                                       :initial-selection-image selection-image
                                       :selection-image selection-image
                                       :changes []})]
          {:db (assoc db :tool tool)})

        (and (= (:type event) :mouse-move) (not user-is-drawing))
        {:db db
         :fx [[:clear-visual-effects]
              [:highlight-pixels [(:pos event)]]]}

        :else {:db db})

      :move-selection
      (cond
        (= (:type event) :mouse-down)
        (if (not (contains? (-> tool :state :selection-image) (:pos event)))
          (commit-moved-selection db)
          {:db db})

        (or (= (:type event) :mouse-down)
            (and (= (:type event) :mouse-move) user-is-drawing))
        (let [{:keys [changes]} (move-selection tool initial-mouse-down-pos event)]
          {:db (assoc-in db [:tool :state :changes] changes)
           :fx [[:clear-visual-effects]
                [:clear-preview]
                [:draw-preview changes]]})

        (= (:type event) :mouse-up)
        (let [{:keys [changes moved-selection-image]} (move-selection tool initial-mouse-down-pos event)
              updated-tool (-> tool
                               (assoc-in [:state :selection-image] moved-selection-image)
                               (assoc-in [:state :changes] changes))]
          {:db (assoc db :tool updated-tool)
           :fx [[:clear-preview]
                [:draw-preview changes]
                [:highlight-selection moved-selection-image]]})))))

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
    (commit-changes-and-init-tool db
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
   (let [size (-> @db/app-db :sprite sprite/get-size)
         canvas (. js/document (getElementById "visual-effects"))]
     (->> selection
          (filter (fn [[pos]] (geometry/valid-point? pos size)))
          (map (fn [[pos color]] [pos (color/get-highlight-color color)]))
          (#(canvas/update-image-data canvas %))))))
