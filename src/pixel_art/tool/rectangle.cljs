(ns pixel-art.tool.rectangle
  (:require [pixel-art.tool.common :refer [update-preview-and-draw commit-preview-changes]]
            [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :rectangle})

(defn behaviour [event db]
  (let [{:keys [color initial-mouse-down-pos]} db]
    (cond
      (#{:mouse-down :mouse-move} (:type event))
      (let [preview (->> (geometry/get-rectange-border-points initial-mouse-down-pos
                                                              (:pos event))
                         (map (fn [p] [p color]))
                         (into {}))]
        (update-preview-and-draw db preview {:clear true}))

      (= :mouse-up (:type event))
      (commit-preview-changes db))))
