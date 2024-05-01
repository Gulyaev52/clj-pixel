(ns pixel-art.tool.pen
  (:require [pixel-art.tool.utils :refer [commit-changes get-tool-options
                                          resize-pixel]]))

(defn init [] {:type :pen :state {:visited-pixels {}}})

(def options-spec
  [{:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}
   {:type :checkbox
    :field :pixel-perfect
    :initial-value false
    :label "Pixel perfect"}
   {:type :checkbox
    :field :mirror-x
    :initial-value false
    :label "Mirror-x"}])

(defn handle-mouse-event [event db]
  (let [{:keys [user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      (let [{:keys [color]} db
            {:keys [pixel-size]} (get-tool-options db)
            new-pixels (->> (resize-pixel (:pos event) pixel-size)
                            (map (fn [p] [p color])))]
        {:db (update-in db [:tool :state :visited-pixels] #(merge % new-pixels))
         :fx [[:draw-preview new-pixels]]})

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels points]]})

      (= :mouse-up (:type event))
      (let [visited-pixels (-> db :tool :state :visited-pixels)]
        (-> db
            (assoc :tool (init))
            (commit-changes visited-pixels))))))
