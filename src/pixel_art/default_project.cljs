(ns pixel-art.default-project
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.model.color :as color]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]))

(defn get-layer-name [type layers-count]
  (str (if (= type :group) "Group " "Layer ") (inc layers-count)))

(def initial-frame-duration 100)

(def default-palettes-and-current-colors
  (let [palettes [{:name "default"
                   :current true
                   :colors (map color/rgba ["black" "red" "green" "blue" "yellow" "gray" "purple"])}]]
    {:palettes palettes
     :primary-color (-> palettes first :colors first)
     :secondary-color (color/rgba 255 0 0)}))

(defn create-empty-sprite [size]
  (sprite/create {:size size
                  :layer (layer/create (get-layer-name :single 0))
                  :frame (frame/create initial-frame-duration)
                  :cel (cel/create size)}))

(def example-project
  (let [sprite-size {:width 8 :height 8}
        secondary-color (:secondary-color default-palettes-and-current-colors)]
    (assoc
     default-palettes-and-current-colors
     :sprite
     (->> (create-empty-sprite sprite-size)
          (sprite/set-current-cel-pixels (->> (for [x (range 0 (:width sprite-size))
                                                    y (range 0 (:height sprite-size))]
                                                [{:x x :y y} color/transparent-color])
                                              (into {})))
          (sprite/set-current-cel-pixels {{:x 0 :y 0} secondary-color
                                          {:x 0 :y 1} color/transparent-color
                                          {:x 1 :y 1} secondary-color
                                          {:x 3 :y 3} secondary-color
                                          {:x 3 :y 4} secondary-color
                                          {:x 4 :y 3} secondary-color
                                          {:x 4 :y 4} secondary-color})))))
