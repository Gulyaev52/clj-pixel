(ns pixel-art.tool.shape-select
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.tool.rectangle-select :as rectangle-select :refer [move-selection remove-transparent-colors]]
            [pixel-art.tool.utils :refer [commit-preview-changes
                                          update-preview-and-draw]]
            [re-frame.core :as re-frame]
            [re-pressed.core :as rp]))

;; todo: поправить цвет подсветки
;; todo: user is drawgin
;; подумать как работать с preview и canvas

(defn init [] {:type :shape-select :state {:mode :select}})

(def options-spec
  [])

(defn unselect [db] (commit-preview-changes db))

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

(defn handle-mouse-event [event db]
  (let [{:keys [tool source-frame initial-mouse-down-pos user-is-drawing]} db]
    (case (-> tool :state :mode)
      :select
      (cond
        (= (:type event) :mouse-down)
        (let [color-under-mouse (frame/get-pixel (:pos event) source-frame)
              selection-points (flood-fill (:pos event)
                                           (frame/get-size source-frame)
                                           #(= (frame/get-pixel % source-frame) color-under-mouse))
              selection-image (->> selection-points
                                   (map (fn [p] [p (frame/get-pixel p source-frame)]))
                                   (into {}))
              tool (assoc tool :state {:mode :move-selection
                                       :initial-selection-image selection-image
                                       :selection-image selection-image})]
          {:db (assoc db :tool tool)
           :highlight-selection [selection-image {:clear true}]
           :dispatch [::rp/set-keydown-rules
                      {:event-keys [[[::cancel-selection]
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
                                       }]]]}]})

        (= (:type event) :mouse-move)
        {:db db
         :highlight-pixels [(:pos event)]}

        :else {:db db})

      :move-selection
      (cond
        (= (:type event) :mouse-down)
        (if (not (contains? (-> tool :state :selection-image) (:pos event)))
          (-> (assoc db :tool (init))
              (commit-preview-changes))
          {:db (assoc-in db [:tool :state :user-is-moving-selection] true)}) ;; todo: переключать мод на up и тогда это можно убрать

        (and (or (= (:type event) :mouse-down)
                 (and (= (:type event) :mouse-move) user-is-drawing))
             (-> tool :state :user-is-moving-selection))
        (let [{:keys [preview]} (move-selection tool initial-mouse-down-pos event)]
          (update-preview-and-draw db preview {:clear true}))

        (and (= (:type event) :mouse-up) (-> tool :state :user-is-moving-selection))
        (let [{:keys [preview moved-selection-image]} (move-selection tool initial-mouse-down-pos event)
              updated-tool (assoc-in tool [:state :selection-image] moved-selection-image)]
          (-> db
              (assoc :tool updated-tool)
              (update-preview-and-draw preview {:clear true})
              (assoc :highlight-selection [moved-selection-image {:clear false}])))

        :else {:db db}))))

(defn- copy-selection [db]
  (let [{:keys [selection-image]} (-> db :tool :state)]
    (-> db
        (assoc
         :selection-manager
         {:selection-image selection-image}))))
;; todo: copy-selection и delete-selection с commit-preview-changes

(defn- delete-selection-and-commit-preview [db]
  (let [{:keys [initial-selection-image pasted?]} (-> db :tool :state)
        deleted-initial-selection (if pasted?
                                    {}
                                    (update-vals initial-selection-image (fn [_] frame/transparent-color)))]
    (-> db
        (assoc :preview deleted-initial-selection)
        (assoc :tool (init))
        (commit-preview-changes)))) ;; todo: тут нет смысла в превью

(defn combine-event-handlers [[ev1 ev2]]
  (fn [a1 a2]
    (let [res1 (ev1 a1 a2)
          res2 (ev2 (assoc a1 :db (:db res1)) a2)]
      (merge res1 res2))))

(re-frame/reg-event-fx
 ::delete-selection
 (fn [{:keys [db]} _]
   (delete-selection-and-commit-preview db)))

(re-frame/reg-event-fx
 ::cancel-selection
 (combine-event-handlers
  [(fn [{:keys [db]}] (commit-preview-changes db))
   (fn [{:keys [db]} _]
     {:db (assoc db :tool (init))})]))

(re-frame/reg-event-fx
 ::copy-selection
 (combine-event-handlers
  [(fn [{:keys [db]}] (commit-preview-changes db))
   (fn [{:keys [db]} _]
     {:db (-> db
              copy-selection
              (assoc :tool (init)))})]))

(re-frame/reg-event-fx
 ::past-selection
 (combine-event-handlers
  [(fn [{:keys [db]}] (commit-preview-changes db))
   (fn [{:keys [db]} _]
     (let [{:keys [selection-image]} (:selection-manager db)
           tool {:type :shape-select
                 :state {:mode :move-selection
                         :initial-selection-image selection-image
                         :selection-image selection-image
                         :pasted? true}}
           preview (remove-transparent-colors selection-image)]
       {:db (assoc db :tool tool :preview preview)
        :draw-preview [preview {:clear true}]
        :highlight-selection [selection-image {:clear false}]}))]))

(re-frame/reg-event-fx
 ::cut-selection
 (fn [{:keys [db]} _]
   (def db db)
   (-> (copy-selection db)
       (delete-selection-and-commit-preview))))
