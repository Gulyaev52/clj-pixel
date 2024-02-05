(ns pixel-art.tool.rectangle-select
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]
            [clojure.set]
            [debux.cs.core :refer-macros [dbgn]]))

;; в истории сохраняется сразу?
;; selection должна накладывать опасити на цвет

(defn transpare-color [color] "t")

(defn- get-offset-pos [initial-pos new-pos]
  (let [x (if (> (:x new-pos) (:x initial-pos))
            (- (:x new-pos) (:x initial-pos))
            (- (:x new-pos) (:x initial-pos)))
        y (if (> (:y new-pos) (:y initial-pos))
            (- (:y new-pos) (:y initial-pos))
            (- (:y new-pos) (:y initial-pos)))]
    {:x x :y y}))

(defn- move-selection [data-r]
  (let [{:keys [tool-state event-pos highlighted? source-frame]} data-r
        {:keys [initial-mouse-down-pos initial-selection]} tool-state
        offset-pos (get-offset-pos initial-mouse-down-pos event-pos)
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
          {:tool (assoc-in tool [:state :initial-mouse-down-pos] (:pos event))
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