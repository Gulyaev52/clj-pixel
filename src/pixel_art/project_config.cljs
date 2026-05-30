(ns pixel-art.project-config
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.model.color :as color]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]))

(def max-sprite-dim 512)

(def max-zoom-scale 80)
(def min-zoom-scale 1)

(def auto-backup-in-ms (* 60000 5)) ;; every 5 min

(def history-max-size)

(defn get-layer-name [type layers-count]
  (str (if (= type :group) "Group " "Layer ") (inc layers-count)))

(def initial-frame-duration 100)

(def default-palettes-and-current-colors
  (let [palettes [{:name "default"
                   :current true
                   :colors (map color/int ["black" "red" "green" "blue" "yellow" "gray" "purple"])}]]
    {:palettes palettes
     :primary-color (-> palettes first :colors first)
     :secondary-color (color/int 255 0 0)}))

(defn create-empty-sprite [title size]
  (sprite/create {:size size
                  :layer (layer/create (get-layer-name :single 0))
                  :frame (frame/create initial-frame-duration)
                  :cel (cel/create size)
                  :title title}))

(defn get-example-project []
  (let [sprite-size {:width 512 :height 512}]
    (assoc
     default-palettes-and-current-colors
     :sprite
     (->> (create-empty-sprite "Example" sprite-size)
          (sprite/set-current-cel-from-pixels-map (for [x (range 0 (:width sprite-size))
                                                   y (range 0 (:height sprite-size))]
                                               [{:x x :y y} (color/int (rand-int 255) (rand-int 255) (rand-int 255))]))))))