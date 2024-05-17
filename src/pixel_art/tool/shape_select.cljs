(ns pixel-art.tool.shape-select
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.tool.rectangle-select :as rectangle-select :refer [copy-selection
                                                                          move-selection
                                                                          remove-transparent-colors]]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool get-current-frame]]
            [re-frame.core :as re-frame]))

;; подумать как работать с preview и canvas

(defn init [] {:type :shape-select :state {:mode :select}})

(def options-spec
  [])

(def hotkeys
  [[[::cancel-selection]
    [{:keyCode 27 ;; esc
      }]]

   [[::copy-selection]
    [{:keyCode 67 ;; c
      :ctrlKey true}]]

   [[::past-selection]
    [{:keyCode 86 ;; v
      :ctrlKey true}]]

   [[::delete-selection]
    [{:keyCode 46 ;; delete
      }]
    [{:keyCode 8 ;; backspace
      }]]

   [[::cut-selection]
    [{:keyCode 88 ;; x
      }]]])

(defn- valid-pos? [{:keys [x y]} {:keys [width height]}]
  (and (and (>= x 0) (< x width))
       (and (>= y 0) (< y height))))

(defn- flood-fill [start-point size pred]
  (let [!fill-stack (atom [start-point])
        !visited-points (atom #{})]
    (while (> (count @!fill-stack) 0)
      (let [point (first @!fill-stack)]
        (swap! !fill-stack #(drop 1 %))
        (when (and (valid-pos? point size)
                   (not (@!visited-points point))
                   (pred point))
          (swap! !visited-points conj point)
          (swap! !fill-stack concat [{:x (inc (:x point))
                                      :y (:y point)}
                                     {:x (dec (:x point))
                                      :y (:y point)}
                                     {:x (:x point)
                                      :y (inc (:y point))}
                                     {:x (:x point)
                                      :y (dec (:y point))}]))))
    @!visited-points))
(comment
  (def matrix {{:x 0 :y 0} "black" {:x 1 :y 0} "black" {:x 2 :y 0} "white"
               {:x 0 :y 1} "white" {:x 1 :y 1} "white" {:x 2 :y 1} "white"})
  (flood-fill {:x 0 :y 0} {:width 8 :height 8} #(= (get matrix %) "black")))

(defn commit-moved-selection [db]
  (let [changes (-> db :tool :state :changes)]
    (commit-changes-and-init-tool db changes (init))))

(defn handle-mouse-event [event db]
  (let [{:keys [tool initial-mouse-down-pos user-is-drawing]} db]
    (case (-> tool :state :mode)
      :select
      (cond
        (= (:type event) :mouse-down)
        (let [current-frame (get-current-frame db)
              color-under-mouse (frame/get-pixel (:pos event) current-frame)
              selection-points (flood-fill (:pos event)
                                           (frame/get-size current-frame)
                                           #(= (frame/get-pixel % current-frame) color-under-mouse))
              selection-image (->> selection-points
                                   (map (fn [p] [p (frame/get-pixel p current-frame)]))
                                   (into {}))
              tool (assoc tool :state {:mode :move-selection
                                       :initial-selection-image selection-image
                                       :selection-image selection-image
                                       :changes []})]
          {:db (assoc db :tool tool)
           :fx [[:clear-preview]
                [:highlight-selection selection-image]]})

        (= (:type event) :mouse-move)
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels [(:pos event)]]]}

        :else {:db db})

      :move-selection
      (cond
        (= (:type event) :mouse-down)
        (if (not (contains? (-> tool :state :selection-image) (:pos event)))
          (commit-moved-selection db)
          {:db (assoc-in db [:tool :state :user-is-moving-selection] true)})

        (and (or (= (:type event) :mouse-down)
                 (and (= (:type event) :mouse-move) user-is-drawing))
             (-> tool :state :user-is-moving-selection))
        (let [{:keys [changes]} (move-selection tool initial-mouse-down-pos event)]
          {:db (assoc-in db [:tool :state :changes] changes)
           :fx [[:clear-preview]
                [:draw-preview changes]]})

        (and (= (:type event) :mouse-up) (-> tool :state :user-is-moving-selection))
        (let [{:keys [changes moved-selection-image]} (move-selection tool initial-mouse-down-pos event)
              updated-tool (-> tool
                               (assoc-in [:state :selection-image] moved-selection-image)
                               (assoc-in [:state :changes] changes))]
          {:db (assoc db :tool updated-tool)
           :fx [[:clear-preview]
                [:draw-preview changes]
                [:highlight-selection moved-selection-image]]})

        :else {:db db}))))

(defn delete-selection-and-commit [db]
  (let [{:keys [initial-selection-image pasted?]} (-> db :tool :state)
        deleted-initial-selection (if pasted?
                                    {}
                                    (update-vals initial-selection-image (fn [_] frame/transparent-color)))]
    (commit-changes-and-init-tool db deleted-initial-selection (init))))

(re-frame/reg-event-fx
 ::delete-selection
 (fn [{:keys [db]} _]
   (delete-selection-and-commit db)))

(re-frame/reg-event-fx
 ::cut-selection
 (fn [{:keys [db]} _]
   (-> (copy-selection db)
       (delete-selection-and-commit))))

(re-frame/reg-event-fx
 ::cancel-selection
 (fn [{:keys [db]} _]
   (commit-moved-selection db)))

(re-frame/reg-event-fx
 ::copy-selection
 (fn [{:keys [db]} _]
   (-> db
       copy-selection
       commit-moved-selection)))

(re-frame/reg-event-fx
 ::past-selection
 ;; todo: нужно что бы это было возможно только после копирования
 (fn [{:keys [db]} _]
   (let [{:keys [selection-image]} (:selection-manager db)
         changes (remove-transparent-colors selection-image)
         new-tool {:type :shape-select
                   :state {:mode :move-selection
                           :initial-selection-image selection-image
                           :selection-image selection-image
                           :changes (remove-transparent-colors selection-image)
                           :pasted? true}}]
     (-> db
         commit-moved-selection
         (assoc-in [:db :tool] new-tool)
         (update :fx #(concat % [[:clear-preview]
                                 [:draw-preview changes]
                                 [:highlight-selection selection-image]]))))))
