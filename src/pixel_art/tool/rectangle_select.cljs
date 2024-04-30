(ns pixel-art.tool.rectangle-select
  (:require [clojure.set]
            [pixel-art.events.event-collector]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.common :refer [commit-preview-changes
                                           update-preview-and-draw]]
            [pixel-art.utils.geometry :as geometry]
            [re-frame.core :as re-frame]
            [re-frame.db :as db]
            [sc.api :as api]
            [re-pressed.core :as rp]))

;; todo: grid; preview + outline clear; удалять хоткеи; нужно помнить о состояния превью; init

;; todo: используем так как из selection-image удаляются прозр точки(не работает днд) и если проверять вхож

(defn init [] {:type :rectangle-select :mode :select})

(defn release [] {})

(defn remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color frame/transparent-color)))
       (into {})))

(defn behaviour [event db]
  (let [{:keys [tool source-frame initial-mouse-down-pos]} db]
    (api/spy)
    (case (or (-> tool :state :mode) :select) ;;todo: use init. todo: а зачем поле state
      :select
      (cond
        (#{:mouse-down :mouse-move} (:type event))
        {:db (assoc-in db [:tool :state] {:user-is-making-selection true})
         :draw-selection-outline-on-preview [initial-mouse-down-pos (:pos event) {:clear true}]}

        (and (= :mouse-up (:type event)) (-> tool :state :user-is-making-selection))
        (let [selection-image (->> (geometry/get-rectange-points initial-mouse-down-pos (:pos event))
                                   (map (fn [p] [p (frame/get-pixel p source-frame)]))
                                   (into {}))
              tool (assoc tool :state {:mode :move-selection
                                       :initial-selection-image selection-image
                                       :selection-image selection-image})]
          {:db (assoc db :tool tool)
           :draw-selection-outline-on-preview [initial-mouse-down-pos (:pos event) {:clear true}]
           :dispatch [::rp/set-keydown-rules
                      {:event-keys [[[::copy-selection]
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

                                    [[::cut-selection] ;;todo: implement
                                     [{:keyCode 88 ;; x
                                       }]]]}]})

        :else {:db db})

      :move-selection
      (cond
        (= (:type event) :mouse-down)
        (if (not (contains? (-> tool :state :selection-image) (:pos event)))
          (-> (assoc db :tool (init))
              (commit-preview-changes))
          {:db db})

        (= (:type event) :mouse-move)
        (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
              {:keys [initial-selection-image selection-image pasted?]} (:state tool)

              deleted-initial-selection (when (not pasted?)
                                          (update-vals initial-selection-image (fn [_] frame/transparent-color)))
              moved-selection-image (-> selection-image
                                        (update-keys #(merge-with + % offset-pos))
                                        remove-transparent-colors)
              preview (merge deleted-initial-selection moved-selection-image)]
          (update-preview-and-draw db preview {:clear true}))

        (= (:type event) :mouse-up)
        (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
              {{:keys [selection-image]} :state} tool

              moved-selection-image (update-keys selection-image #(merge-with + % offset-pos))
              updated-tool (assoc-in tool [:state :selection-image] moved-selection-image)

              {:keys [top-left bottom-right]} (geometry/get-rectange-top-left-and-bottom-right (keys moved-selection-image))]
          {:db (assoc db :tool updated-tool)
           :draw-selection-outline-on-preview [top-left bottom-right {:clear false}]})))))

(defn copy-selection [db]
  (let [{:keys [selection-image]} (-> db :tool :state)]
    (-> db
        (assoc
         :selection-manager
         {:selection-image selection-image}))))
;; todo: copy-selection и delete-selection с commit-preview-changes

(defn delete-selection [db]
  (let [{:keys [initial-selection-image pasted?]} (-> db :tool :state)
        deleted-initial-selection (if pasted?
                                    {}
                                    (update-vals initial-selection-image (fn [_] frame/transparent-color)))]
    (-> db
        (assoc :preview deleted-initial-selection)
        (assoc :tool (init))
        (commit-preview-changes)))) ;; todo: тут нет смысла в превью

(re-frame/reg-event-fx
 ::delete-selection
 (fn [{:keys [db]} _]
   (delete-selection db)))

(re-frame/reg-event-fx
 ::copy-selection
 (fn [{:keys [db]} _]
   (-> db
       (commit-preview-changes) ;; сперва коммитим те изменения что были до копирования
       (update :db #(-> %
                        copy-selection
                        (assoc :tool (init)))))))

(re-frame/reg-event-fx
 ::past-selection
 (fn [{:keys [db]} _]
   (let [{:keys [selection-image]} (-> db :selection-manager)
         tool {:type :rectangle-select
               :state {:mode :move-selection
                       :initial-selection-image selection-image
                       :selection-image selection-image
                       :pasted? true}}
         preview (remove-transparent-colors selection-image)
         {:keys [top-left bottom-right]} (geometry/get-rectange-top-left-and-bottom-right (keys selection-image))]
     (-> db
         (commit-preview-changes) ;; сперва коммитим те изменения что были до вставки
         (update :db #(assoc % :tool tool :preview preview))
         (assoc :draw-preview [preview {:clear true}])
         (assoc :draw-selection-outline-on-preview [top-left bottom-right {:clear false}])))))

(re-frame/reg-event-fx
 ::cut-selection
 (fn [{:keys [db]} _]
   (def db db)
   (-> (copy-selection db)
       (delete-selection))))

(re-frame/reg-fx
 :draw-selection-outline-on-preview
 (fn [[p1 p2 {:keys [clear]}]]
   (let [db @db/app-db

         {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points p1 p2)
         width (inc (- (:x bottom-right) (:x top-left)))
         height (inc (- (:y bottom-right) (:y top-left)))

         canvas (. js/document (getElementById "preview"))
         ctx (. canvas (getContext "2d"))

         frame-size (frame/get-size (:source-frame db))
         scale (:scale db)
         canvas-size {:width (* scale (:width frame-size))
                      :height (* scale (:height frame-size))}]
     (when clear
       (. ctx (clearRect 0 0 (:width canvas-size) (:height canvas-size))))
     (set! (.-strokeStyle ctx) "#ffffff")
     (.setLineDash ctx #js [5])
     (.strokeRect ctx
                  (* (:x top-left) scale)
                  (* (:y top-left) scale)
                  (* width scale)
                  (* height scale)))))
