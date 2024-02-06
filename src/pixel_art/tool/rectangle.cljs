(ns pixel-art.tool.rectangle
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :rectangle})

(defn behaviour [event data]
  (let [{:keys [source-frame overlay-frame color tool]} data]
    (case (:type event)
      :mouse-down
      {:tool (assoc tool :state {:initial-mouse-down-pos (:pos event)})
       :overlay-frame (frame/set-pixels [{:pos (:pos event) :color color}] source-frame)}

      :mouse-move
      {:overlay-frame (->> (geometry/get-rectange-border-points (-> tool :state :initial-mouse-down-pos)
                                                                (:pos event))
                           (map (fn [p] {:pos p :color color}))
                           (#(frame/set-pixels % source-frame)))}

      :mouse-up
      {:overlay-frame overlay-frame
       :commit-changes true})))
