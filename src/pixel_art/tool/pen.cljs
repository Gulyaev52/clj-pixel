(ns pixel-art.tool.pen
  (:require [pixel-art.model.frame :as frame]))

(defn init [] {:type :pen})

(defn behaviour [event db]
  (let [{:keys [overlay-frame color]} db]
    (cond
      (#{:mouse-down :mouse-move} (:type event))
      {:overlay-frame (frame/set-pixels [{:pos (:pos event) :color color}] overlay-frame)}

      (= :mouse-up (:type event))
      {:overlay-frame overlay-frame
       :commit-changes true})))
