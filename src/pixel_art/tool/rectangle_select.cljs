(ns pixel-art.tool.rectangle-select
  (:require ["tinycolor2" :as tinycolor]
            [clojure.set]
            [pixel-art.events.event-collector]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool get-current-frame]]
            [pixel-art.utils.geometry :as geometry]
            [re-frame.core :as re-frame]
            [re-frame.db :as db]))

;; удалять хоткеи; нужно помнить о состояния превью; init
;; todo: используем так как из selection-image удаляются прозр точки(не работает днд) и если проверять вхож
;; todo: а зачем поле state
;; ресайз

(defn init [] {:type :rectangle-select :state {:mode :select}})

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

(def options-spec
  [])

(defn remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color frame/transparent-color)))
       (into {})))

(defn move-selection [tool initial-mouse-down-pos event]
  (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
        {:keys [initial-selection-image selection-image pasted?]} (:state tool)

        deleted-initial-selection (when (not pasted?)
                                    (update-vals initial-selection-image (fn [_] frame/transparent-color)))
        moved-selection-image (-> selection-image
                                  (update-keys #(merge-with + % offset-pos)))
        changes (merge deleted-initial-selection
                       (remove-transparent-colors moved-selection-image))]
    {:changes changes ;; todo: а это точно нужно?
     :moved-selection-image moved-selection-image}))

(defn- get-rectangle-selection-image [p1 p2 current-frame]
  (->> (geometry/get-rectange-points p1 p2)
       (map (fn [p] [p (frame/get-pixel p current-frame)]))
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
         :fx [[:clear-preview]
              [:highlight-selection (get-rectangle-selection-image initial-mouse-down-pos
                                                                   (:pos event)
                                                                   (get-current-frame db))]]}

        (and (= (:type event) :mouse-move) (not user-is-drawing))
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels [(:pos event)]]]}

        (and (= :mouse-up (:type event)) (-> tool :state :user-is-making-selection))
        (let [selection-image (get-rectangle-selection-image initial-mouse-down-pos
                                                             (:pos event)
                                                             (get-current-frame db))
              tool (assoc tool :state {:mode :move-selection
                                       :initial-selection-image selection-image
                                       :selection-image selection-image
                                       :changes []})]
          {:db (assoc db :tool tool)
           :fx [[:clear-preview]
                [:highlight-selection selection-image]]})

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
           :fx [[:clear-preview]
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
         {:selection-image selection-image}))))

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
         new-tool {:type :rectangle-select
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

(defn- get-highlight-color [color]
  (let [dark-color "rgba(0, 0, 0, 0.2)"
        light-color "rgba(255, 255, 255, 0.2)"]
    (if (= color frame/transparent-color)
      dark-color
      (let [luminance (.. (tinycolor color) toHsl -l)]
        (if (> luminance 0.5)
          dark-color
          light-color)))))

(re-frame/reg-fx
 :highlight-selection
 (fn [selection]
   (let [db @re-frame.db/app-db
         canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))
         scale (:scale db)]
     (doseq [[pos color] selection]
       (set! (. ctx -fillStyle) (get-highlight-color color))
       (. ctx (fillRect (* (:x pos) scale) (* (:y pos) scale) scale scale))))))
