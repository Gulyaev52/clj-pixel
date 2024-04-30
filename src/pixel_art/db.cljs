(ns pixel-art.db
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.tool.core :as tool]))

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(def default-db
  (let [size {:width 8 :height 8}
        frame (->> (frame/create size)
                   (frame/set-pixels (flatten
                                      (for [x (range 0 8)
                                            y (range 0 8)]
                                        {:pos {:x x :y y} :color "green"})))
                   (frame/set-pixels
                    [{:pos {:x 0 :y 0} :color "black"}
                     {:pos {:x 0 :y 1} :color frame/transparent-color}
                     {:pos {:x 1 :y 1} :color "black"}]))]
    {:size size
     :source-frame frame
     :preview {}
     :tool (tool/init :pen)
     :tools-options (get-initial-options tool/options-specs) ;; todo: добавить type в модуль; иметь какой-то массив со всеми опц
     :color "black"
     :selection-manager {}
     :scale 40}))
