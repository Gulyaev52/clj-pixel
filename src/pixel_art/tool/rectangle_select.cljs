(ns pixel-art.tool.rectangle-select
  (:require ["tinycolor2" :as tinycolor]
            [clojure.set]
            [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]
            [re-frame.core :as re-frame]
            [re-pressed.core :as rp]
            [sc.api :as api]
            [debux.cs.core :as debux :refer-macros [dbgn]]))

(defn init [] {:type :rectangle-select :mode :select})

(defn get-transparent-color [color]
  (if color
    (.. (tinycolor (or color "white"))
        (setAlpha 0.8)
        (toRgbString))
    "rgba(160, 215, 240, 0.6)"))

(defn- get-offset-pos [initial-pos new-pos]
  (let [x (if (> (:x new-pos) (:x initial-pos))
            (- (:x new-pos) (:x initial-pos))
            (- (:x new-pos) (:x initial-pos)))
        y (if (> (:y new-pos) (:y initial-pos))
            (- (:y new-pos) (:y initial-pos))
            (- (:y new-pos) (:y initial-pos)))]
    {:x x :y y}))

(defn- move-selection [{:keys [event]} db]
  (let [{:keys [tool initial-mouse-down-pos source-frame]} db
        {:keys [initial-selection pasted?]} (:state tool)

        initial-selection-pos (set (map :pos initial-selection))
        offset-pos (get-offset-pos initial-mouse-down-pos (:pos event))
        new-selection (map (fn [{:keys [pos color]}]
                             (let [new-pos {:x (+ (:x pos) (:x offset-pos))
                                            :y (+ (:y pos) (:y offset-pos))}]
                               {:pos new-pos
                                :color (cond
                                         (initial-selection-pos new-pos)
                                         color

                                         (= color frame/transparent-color)
                                         (frame/get-pixel new-pos source-frame)

                                         :else color)}))
                           initial-selection)


        cuted-selection (if pasted?
                          []
                          (->> (clojure.set/difference (set (map :pos initial-selection))
                                                       (set (map :pos new-selection)))
                               (map (fn [pos] {:pos pos :color frame/transparent-color}))))]
    {:overlay-frame (->> source-frame
                         (frame/set-pixels new-selection)
                         (frame/set-pixels cuted-selection))
     :new-selection new-selection}))

(defn- highlight-selection [selection]
  (map #(update % :color get-transparent-color) selection))

(defn behaviour [event db]
  (let [{:keys [source-frame overlay-frame tool initial-mouse-down-pos]} db]
    ;; todo: init ?
    (case (or (-> tool :state :mode) :select)
      :select
      (cond
        (#{:mouse-down :mouse-move} (:type event))
        (let [selection-points (geometry/get-rectange-points initial-mouse-down-pos
                                                             (:pos event))
              initial-selection (map (fn [pos color] {:pos pos :color color})
                                     selection-points
                                     (frame/get-pixels selection-points source-frame))
              highlighted-selection (highlight-selection initial-selection)]
          {:tool (update tool :state #(merge % {:initial-selection initial-selection
                                                :selection initial-selection}))
           :overlay-frame (frame/set-pixels highlighted-selection source-frame)})

        (and (= :mouse-up (:type event)) (-> tool :state :selection))
        {:tool (assoc-in tool [:state :mode] :move-selection)
         :overlay-frame overlay-frame
         :effects {:dispatch [::rp/set-keydown-rules
                              {:event-keys [[[::copy-selection (-> tool :state :initial-selection)]
                                             [{:keyCode 67 ;; c
                                               :ctrlKey true}]]

                                            [[::past-selection] ;; todo: нужно коммитить текущ
                                             [{:keyCode 86 ;; v
                                               :ctrlKey true}]]

                                            [[::delete-selection (-> tool :state :initial-selection)]
                                             [{:keyCode 46 ;; delete
                                               }]
                                             [{:keyCode 8 ;; backspace
                                               }]]]}]}})

      :move-selection
      (cond
        (= :mouse-down (:type event))
        (if (some #{(:pos event)} (map :pos (-> tool :state :selection)))
          {:overlay-frame overlay-frame}
          {:tool (assoc tool :state {:mode :select})
           :overlay-frame (->> overlay-frame
                               (frame/set-pixels (-> tool :state :selection)))
           :commit-changes true})

        (= :mouse-move (:type event))
        (let [{:keys [overlay-frame new-selection]} (move-selection {:event event
                                                                     :highlighted? false}
                                                                    db)]
          {:tool (assoc-in tool [:state :selection] new-selection)
           :overlay-frame overlay-frame})

        (= :mouse-up (:type event))
        {:tool (assoc tool :state {:mode :select})
         :overlay-frame (->> overlay-frame
                             (frame/set-pixels (-> tool :state :selection)))
         :commit-changes true}))))

(re-frame/reg-event-fx
 ::delete-selection
;;  удаляем там где был +
;; удаляем само выделение -
 (fn [{:keys [db]} [_ selection]]
   (let [cutted-selection (map (fn [{:keys [pos]}] {:pos pos :color frame/transparent-color}) selection)
         overlay-frame (->> (:overlay-frame db)
                            (frame/set-pixels cutted-selection))]
     {:db (assoc db
                 :overlay-frame overlay-frame
                 :source-frame overlay-frame
                 :tool {:type :rectangle-select
                        :mode :select})
      :draw-frame overlay-frame})))

(re-frame/reg-event-fx
 ::copy-selection
 (fn [{:keys [db]} [_ selection]]
   (let [db (-> db
                (assoc-in
                 [:selection-manager :copied-selection]
                 selection)
                (assoc-in
                 [:tool :state]
                 {:mode :select})
                (assoc :overlay-frame (:source-frame db)))]
     {:db db
      :draw-frame (:overlay-frame db)})))

(re-frame/reg-event-fx
 ::past-selection
 (fn [{:keys [db]} _]
   (let [selection (-> db :selection-manager :copied-selection)
         overlay-frame (frame/set-pixels (highlight-selection selection)
                                         (:overlay-frame db))]
     {:db (assoc db
                 :tool
                 {:type :rectangle-select
                  :state {:mode :move-selection
                          :initial-selection selection
                          :selection selection
                          :pasted? true}}
                 :overlay-frame overlay-frame)
      :draw-frame overlay-frame})))
