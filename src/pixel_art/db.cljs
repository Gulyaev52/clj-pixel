(ns pixel-art.db
  (:require [pixel-art.model.frame :as frame]))

(def default-db
  (let [size {:width 8
              :height 8}
        frame (->> (frame/create size)
                   (frame/set-pixels (flatten
                                      (for [x (range 0 8)
                                            y (range 0 8)]
                                        {:pos {:x x :y y} :color "green"})))
                   (frame/set-pixels
                    [{:pos {:x 0 :y 0} :color "black"}
                     {:pos {:x 1 :y 1} :color "black"}]))]
    {:size size
     :source-frame frame
     :overlay-frame frame
     :tool {:type :pen}
     :color "black"
     :selection-manager {}
     :scale 40}))
