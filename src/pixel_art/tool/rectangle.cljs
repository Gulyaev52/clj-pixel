(ns pixel-art.tool.rectangle
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :rectangle})

(defn behaviour [event db]
  (let [{:keys [source-frame overlay-frame color initial-mouse-down-pos]} db]
    (cond
      (#{:mouse-down :mouse-move} (:type event))
      {:overlay-frame (->> (geometry/get-rectange-border-points initial-mouse-down-pos
                                                                (:pos event))
                           (map (fn [p] {:pos p :color color}))
                           (#(frame/set-pixels % source-frame)))}

      (= :mouse-up (:type event))
      {:overlay-frame overlay-frame
       :commit-changes true})))
