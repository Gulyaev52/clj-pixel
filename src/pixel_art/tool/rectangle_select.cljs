(ns pixel-art.tool.rectangle-select
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]
            [clojure.set]))

;; 2 состояния: select, moveSelection


;; select -> moveSelection + стейт

;; moveSelection
;; когда select и mouse down вне, то сбрасываем селектион и коммитим изменения
;; когда select и mouse down внутри то двигаем

;; в истории сохраняется сразу?
;; selection должна накладывать опасити на цвет

(def data pixel-art.tool.pen-test/data)

(defn transpare-color [color] color)

(defn move-selection [{:keys [tool-state event-pos highlighted? source-frame]}]
  (let [{:keys [initial-mouse-down-pos initial-selection]} tool-state
        offset-pos {:x (- (:x initial-mouse-down-pos)
                          (:x event-pos))
                    :y (- (:y initial-mouse-down-pos)
                          (:y event-pos))}
        new-selection (map (fn [{:keys [pos color]}]
                             {:pos {:x (+ (:x pos) (:x offset-pos))
                                    :y (+ (:y pos) (:y offset-pos))}
                              :color (if highlighted?
                                       (transpare-color color)
                                       color)})
                           initial-selection)
        cuted-selection (->> (clojure.set/difference (set (map :pos initial-selection))
                                                     (set (map :pos new-selection)))
                             (map (fn [pos] {:pos pos :color frame/transparent-color})))]
    {:overlay-frame (->> source-frame
                         (frame/set-pixels new-selection)
                         (frame/set-pixels cuted-selection))
     :new-selection new-selection}))

(defn behaviour [data]
  (let [{:keys [event source-frame overlay-frame tool]} data]
    ;; todo: init ?
    (case (or (-> tool :state :mode) :select)
      :select
      (cond
        (= :mouse-down (:type event))
        (let [color (frame/get-pixel (:pos event) source-frame)]
          {:tool (assoc tool :state {:initial-mouse-down-pos (:pos event)})
           :overlay-frame
           (frame/set-pixels [{:pos (:pos event)
                               :color (transpare-color color)}]
                             source-frame)})

        (= :mouse-move (:type event))
        (let [selection-points (geometry/get-rectange-points (-> tool :state :initial-mouse-down-pos)
                                                             (:pos event))
              initial-selection (map (fn [pos color] {:pos pos :color color})
                                     selection-points
                                     (frame/get-pixels selection-points source-frame))
              highlighted-selection (map #(update % :color transpare-color) initial-selection)]
          {:tool (update tool :state #(merge % {:initial-selection initial-selection
                                                :selection initial-selection}))
           :overlay-frame (frame/set-pixels highlighted-selection source-frame)})

        (= :mouse-up (:type event))
        {:tool (assoc-in tool [:state :mode] :move-selection)
         :overlay-frame overlay-frame})

      :move-selection
      (cond
        (= :mouse-down (:type event))
        (if (some #{(:pos event)} (map :pos (-> tool :state :selection)))
          {:tool (assoc tool :state {:initial-mouse-down-pos (:pos event)})
           :overlay-frame overlay-frame}
          {:tool (assoc tool :state {:mode :select})
           :overlay-frame (-> (move-selection {:tool-state (:state tool)
                                               :event-pos (:pos event)
                                               :highlighted? false
                                               :source-frame source-frame})
                              :overlay-frame)
           :commit-changes true})

        (= :mouse-move (:type event))
        (let [{:keys [overlay-frame new-selection]} (move-selection {:tool-state (:state tool)
                                                                     :event-pos (:pos event)
                                                                     :highlighted? false
                                                                     :source-frame source-frame})]
          {:tool (assoc-in tool [:state :selection] new-selection)
           :overlay-frame overlay-frame})

        (= :mouse-up (:type event))
        {:overlay-frame (-> (move-selection {:tool-state (:state tool)
                                             :event-pos (:pos event)
                                             :highlighted? true
                                             :source-frame source-frame})
                            :overlay-frame)}))))
