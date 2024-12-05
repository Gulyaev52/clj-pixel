(ns pixel-art.project-settings
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.model.color :as color]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]))
;; todo: rename to config

(def max-sprite-dim 512) ;; todo: check this value

(def max-scale 80)
(def min-scale 0.5)

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

(defn create-empty-sprite [size]
  (sprite/create {:size size
                  :layer (layer/create (get-layer-name :single 0))
                  :frame (frame/create initial-frame-duration)
                  :cel (cel/create size)}))

(defn get-example-project []
  (let [sprite-size {:width 512 :height 512}]
    (assoc
     default-palettes-and-current-colors
     :sprite
     (->> (create-empty-sprite sprite-size)
          (sprite/set-current-cel-pixels (for [x (range 0 (:width sprite-size))
                                               y (range 0 (:height sprite-size))]
                                           [{:x x :y y} (color/int (rand-int 255) (rand-int 255) (rand-int 255))]))))))

(defn get-viewport-scroll-pos-to-center [canvas-size drawing-container-size viewport-size]
  (let [scroll-dim 15
        canvas-offset-y (/ (:height drawing-container-size) 2)
        absolute-canvas-y (+ canvas-offset-y (/ (:height canvas-size) 2))
        canvas-y-middle (- absolute-canvas-y
                           (/ (- (:height viewport-size) scroll-dim) 2)
                           (/ (:height canvas-size) 2))
        canvas-offset-x (/ (:width drawing-container-size) 2)
        absolute-canvas-x (+ canvas-offset-x (/ (:width canvas-size) 2))
        canvas-x-middle (- absolute-canvas-x
                           (/ (- (:width viewport-size) scroll-dim) 2)
                           (/ (:width canvas-size) 2))]
    {:x canvas-x-middle :y canvas-y-middle}))

(defn get-initial-scale [sprite-size viewport-size]
  (let [[dim dim-value] (apply max-key second sprite-size)
        offset 100]
    (min (/ (- (dim viewport-size) offset) dim-value) max-scale)))

;; todo: rename
;; todo: а надо ли это? мб просто вызвать scrollIntoView
(defn get-initial-drawing-settings [sprite viewport-size]
  (let [scale (get-initial-scale (:size sprite) viewport-size)
        canvas-size (update-vals (:size sprite) #(* % scale))
        empty-space-dim 1500
        drawing-container-size (update-vals canvas-size #(+ % empty-space-dim))
        viewport-scroll (get-viewport-scroll-pos-to-center canvas-size drawing-container-size viewport-size)]
    {:scale scale :drawing-container-size drawing-container-size :viewport-scroll viewport-scroll}))
