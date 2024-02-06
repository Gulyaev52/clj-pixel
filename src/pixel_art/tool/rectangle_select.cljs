(ns pixel-art.tool.rectangle-select
  (:require ["tinycolor2" :as tinycolor]
            [clojure.set]
            [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]
            [sc.api :as api]))

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

(defn- move-selection [{:keys [event highlighted?]} data-r]
  (let [{:keys [tool initial-mouse-down-pos source-frame]} data-r
        {:keys [initial-selection]} (:state tool)
        offset-pos (get-offset-pos initial-mouse-down-pos (:pos event))
        new-selection (map (fn [{:keys [pos color]}]
                             {:pos {:x (+ (:x pos) (:x offset-pos))
                                    :y (+ (:y pos) (:y offset-pos))}
                              :color (if highlighted?
                                       (get-transparent-color color)
                                       color)})
                           initial-selection)
        cuted-selection (->> (clojure.set/difference (set (map :pos initial-selection))
                                                     (set (map :pos new-selection)))
                             (map (fn [pos] {:pos pos :color frame/transparent-color})))]
    {:overlay-frame (->> source-frame
                         (frame/set-pixels new-selection)
                         (frame/set-pixels cuted-selection))
     :new-selection new-selection}))

(defn behaviour [event db]
  (let [{:keys [source-frame overlay-frame tool initial-mouse-down-pos]} db]
    (api/spy)
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
              highlighted-selection (map #(update % :color get-transparent-color) initial-selection)]
          {:tool (update tool :state #(merge % {:initial-selection initial-selection
                                                :selection initial-selection}))
           :overlay-frame (frame/set-pixels highlighted-selection source-frame)})

        (and (= :mouse-up (:type event)) (-> tool :state :selection))
        {:tool (assoc-in tool [:state :mode] :move-selection)
         :overlay-frame overlay-frame})

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
