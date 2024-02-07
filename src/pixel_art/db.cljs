(ns pixel-art.db
  (:require [pixel-art.model.frame :as frame]))

(def default-db
  (let [size {:width 32
              :height 32}
        frame (->> (frame/create size)
                   (frame/set-pixels [{:pos {:x 0 :y 0} :color "black"}
                                      {:pos {:x 1 :y 1} :color "black"}]))]
    {:size size
     :source-frame frame
     :overlay-frame frame
     :tool {:type :pen}
     :color "black"
     :selection-manager {}
     :scale 20}))
