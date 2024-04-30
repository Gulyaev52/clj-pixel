(ns pixel-art.db
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.tool.pen :as pen]))

(def options-ui
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
     :tool (pen/init)
     :tools-options (get-initial-options {:pen pen/options-spec}) ;; todo: добавить type в модуль; иметь какой-то массив со всеми опц
     :color "black"
     :selection-manager {}
     :scale 40}))
