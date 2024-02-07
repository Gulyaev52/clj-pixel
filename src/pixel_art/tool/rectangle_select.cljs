(ns pixel-art.tool.rectangle-select
  (:require ["tinycolor2" :as tinycolor]
            [clojure.set]
            [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]
            [re-frame.core :as re-frame]
            [re-pressed.core :as rp]))

(defn init [] {:type :rectangle-select :mode :select})

(defn get-transparent-color [color]
  (if color
    (.. (tinycolor (or color "white"))
        (setAlpha 0.8)
        (toRgbString))
    "rgba(160, 215, 240, 0.6)"))

(defn- highlight-selection [selection]
  (map #(update % :color get-transparent-color) selection))

(defn- move-selection [{:keys [event highlighted?]} db]
  (let [{:keys [tool last-mouse-pos source-frame]} db
        {:keys [initial-selection selection pasted?]} (:state tool)

        offset-pos (merge-with - (:pos event) last-mouse-pos)
        new-selection (->> selection
                           (map (fn [{:keys [pos color]}]
                                  (let [new-pos (merge-with + pos offset-pos)]
                                    {:pos new-pos
                                     :color (if (= color frame/transparent-color)
                                              (frame/get-pixel new-pos source-frame)
                                              color)}))))
        _ (println pasted?)
        cuted-selection (if pasted?
                          []
                          (map #(assoc % :color frame/transparent-color) initial-selection))]
    {:overlay-frame (->> source-frame
                         (frame/set-pixels cuted-selection)
                         (frame/set-pixels (if highlighted?
                                             (highlight-selection new-selection)
                                             new-selection)))
     :new-selection new-selection}))

(defn behaviour [event db]
  (let [{:keys [source-frame overlay-frame tool initial-mouse-down-pos]} db]
    (case (or (-> tool :state :mode) :select)
      :select
      (cond
        (#{:mouse-down :mouse-move} (:type event))
        (let [selection-points (geometry/get-rectange-points initial-mouse-down-pos
                                                             (:pos event))
              initial-selection (frame/get-pixels-with-pos selection-points source-frame)
              highlighted-selection (highlight-selection initial-selection)]
          {:tool (update tool :state #(merge % {:initial-selection initial-selection
                                                :selection initial-selection}))
           :overlay-frame (frame/set-pixels highlighted-selection source-frame)})

        (and (= :mouse-up (:type event)) (-> tool :state :selection))
        {:tool (assoc-in tool [:state :mode] :move-selection)
         :overlay-frame overlay-frame
         :effects {:dispatch [::rp/set-keydown-rules
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
                                               }]]]}]}})

      :move-selection
      (cond
        (= :mouse-down (:type event))
        (if (some #{(:pos event)} (map :pos (-> tool :state :selection)))
          {:overlay-frame overlay-frame}
          {:tool (assoc tool :state {:mode :select})
           :overlay-frame (frame/set-pixels (-> tool :state :selection) overlay-frame)
           :commit-changes true})

        (= :mouse-move (:type event))
        (let [{:keys [overlay-frame new-selection]} (move-selection {:event event
                                                                     :highlighted? false}
                                                                    db)]
          {:tool (assoc-in tool [:state :selection] new-selection)
           :overlay-frame overlay-frame})

        (= :mouse-up (:type event))
        (let [{:keys [overlay-frame new-selection]} (move-selection {:event event
                                                                     :highlighted? true}
                                                                    db)]
          {:tool (assoc-in tool [:state :selection] new-selection)
           :overlay-frame overlay-frame})))))

(re-frame/reg-event-fx
 ::delete-selection
 (fn [{:keys [db]} _]
   (let [{:keys [initial-selection pasted?]} (-> db :tool :state)
         cutted-initial-selection (if pasted?
                                    []
                                    (map (fn [{:keys [pos]}] {:pos pos :color frame/transparent-color})
                                         initial-selection))
         source-frame (->> (:source-frame db)
                           (frame/set-pixels cutted-initial-selection))]
     {:db (assoc db
                 :overlay-frame source-frame
                 :source-frame source-frame
                 :tool {:type :rectangle-select
                        :mode :select})
      :draw-frame source-frame})))

(re-frame/reg-event-fx
 ::copy-selection
 (fn [{:keys [db]} _]
   (let [{:keys [initial-selection selection]} (-> db :tool :state)
         source-frame (->> (:source-frame db)
                           (frame/set-pixels (map (fn [{:keys [pos]}] {:pos pos :color frame/transparent-color})
                                                  initial-selection))
                           (frame/set-pixels selection))
         db (-> db
                (assoc-in
                 [:selection-manager :copied-selection]
                 selection)
                (assoc-in
                 [:tool :state]
                 {:mode :select})
                (assoc :overlay-frame source-frame)
                (assoc :source-frame source-frame))]
     {:db db
      :draw-frame (:overlay-frame db)})))

(re-frame/reg-event-fx
 ::past-selection
 (fn [{:keys [db]} _]
   (let [copied-selection (-> db :selection-manager :copied-selection)
         {:keys [initial-selection selection]} (-> db :tool :state)
         source-frame (->> (:source-frame db)
                           (frame/set-pixels (map (fn [{:keys [pos]}] {:pos pos :color frame/transparent-color})
                                                  initial-selection))
                           (frame/set-pixels selection))
         overlay-frame (frame/set-pixels (highlight-selection copied-selection)
                                         source-frame)
         tool {:type :rectangle-select
               :state {:mode :move-selection
                       :initial-selection copied-selection
                       :selection copied-selection
                       :pasted? true}}]
     {:db (assoc db
                 :tool tool
                 :overlay-frame overlay-frame
                 :source-frame source-frame)
      :draw-frame overlay-frame})))
