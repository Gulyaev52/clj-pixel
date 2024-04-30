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
;; todo: а зачем поле state
;; ресайз
;; а нужно ли сбрасывать селектион на копирование и вставку и тогда зачем там вообще комитить что-то

(defn init [] {:type :rectangle-select :state {:mode :select}})

(def options-spec
  [])

(defn unselect [db] (commit-preview-changes db))

(defn- remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color frame/transparent-color)))
       (into {})))

(defn- move-selection [tool initial-mouse-down-pos event]
  (let [offset-pos (merge-with - (:pos event) initial-mouse-down-pos)
        {:keys [initial-selection-image selection-image pasted?]} (:state tool)

        deleted-initial-selection (when (not pasted?)
                                    (update-vals initial-selection-image (fn [_] frame/transparent-color)))
        moved-selection-image (-> selection-image
                                  (update-keys #(merge-with + % offset-pos)))
        preview (merge deleted-initial-selection
                       (remove-transparent-colors moved-selection-image))]
    {:preview preview :moved-selection-image moved-selection-image}))


(defn handle-mouse-event [event db]
  (let [{:keys [tool source-frame initial-mouse-down-pos user-is-drawing]} db]
    (api/spy)
    (case (-> tool :state :mode)
      :select
      (cond
        (or (= (:type event) :mouse-down)
            (and (= (:type event) :mouse-move) user-is-drawing))
        {:db (assoc-in db [:tool :state :user-is-making-selection] true)
         :draw-selection-outline-on-preview [initial-mouse-down-pos (:pos event) {:clear true}]}

        (= (:type event) :mouse-move)
        {:db db
         :highlight-pixels [(:pos event)]}

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

        :else {:db db})

      :move-selection
      (cond
        (= (:type event) :mouse-down)
        (if (not (contains? (-> tool :state :selection-image) (:pos event)))
          (-> (assoc db :tool (init))
              (commit-preview-changes))
          {:db db})

        (or (= (:type event) :mouse-down)
            (and (= (:type event) :mouse-move) user-is-drawing))
        (let [{:keys [preview]} (move-selection tool initial-mouse-down-pos event)]
          (update-preview-and-draw db preview {:clear true}))

        (= (:type event) :mouse-up)
        (let [{:keys [preview moved-selection-image]} (move-selection tool initial-mouse-down-pos event)
              updated-tool (assoc-in tool [:state :selection-image] moved-selection-image)
              {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points (keys moved-selection-image))]
          (-> db
              (assoc :tool updated-tool)
              (update-preview-and-draw preview {:clear true})
              (assoc :draw-selection-outline-on-preview [top-left bottom-right {:clear false}])))))))

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
           tool {:type :rectangle-select
                 :state {:mode :move-selection
                         :initial-selection-image selection-image
                         :selection-image selection-image
                         :pasted? true}}
           preview (remove-transparent-colors selection-image)
           {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points (keys selection-image))]
       {:db (assoc db :tool tool :preview preview)
        :draw-preview [preview {:clear true}]
        :draw-selection-outline-on-preview [top-left bottom-right {:clear false}]}))]))

(re-frame/reg-event-fx
 ::cut-selection
 (fn [{:keys [db]} _]
   (def db db)
   (-> (copy-selection db)
       (delete-selection-and-commit-preview))))

(re-frame/reg-fx
 :draw-selection-outline-on-preview
 (fn [[p1 p2 {:keys [clear]}]]
   (let [db @db/app-db

         {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points [p1 p2])
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
