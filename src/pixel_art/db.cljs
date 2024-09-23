(ns pixel-art.db
  (:require ["tinycolor2" :as tinycolor]
            [pixel-art.export :as export]
            [pixel-art.history :as history]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.onion-skin :as onion-skin]
            [pixel-art.palette :as palette]
            [pixel-art.sprite-preview :as preview]
            [pixel-art.tool.core :as tool]
            [sc.api]
            [pixel-art.sprite-resizer :as sprite-resizer]))

(def max-scale 80)

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-layer-name [type layers-count]
  (str (if (= type :group) "Group " "Layer ") (inc layers-count)))

(def initial-frame-duration 100)

(def initial-palettes
  [{:name "default"
    :current true
    :colors (map #(. (tinycolor %) toRgbString) ["black" "red" "green" "blue" "yellow" "gray" "purple"])}])

(defn get-db [{:keys [sprite palettes primary-color secondary-color]}]
  (let [viewport-size {:width 900 :height 700}
        scale max-scale
        canvas-size (update-vals (sprite/get-size sprite) #(* % scale))
        drawing-container-size (update-vals canvas-size #(+ % 1500))]
    (-> {:size (sprite/get-size sprite) ;; todo: size можно получить из спрайта
         :sprite sprite
         :tool (tool/init :pen)
         :tools-options (get-initial-options tool/options-specs)
         :primary-color transparent-color ;; todo: а тут точно так а не индексами? на инит может быть ерунда
         :secondary-color secondary-color ;; todo: а тут точно так а не индексами? на инит может быть ерунда
         :selection-manager {}
         :scale scale
         :viewport-size viewport-size
         :viewport-scroll {:x 700 :y 700}
         :drawing-container-size drawing-container-size
         :onion-skin (onion-skin/init)
         :history (history/init {:sprite sprite})
         :sprite-preview (preview/init)
         :pixels-grid-enabled true
         :palettes (palette/init palettes)
         :export (export/init)
         :sprite-resizer (sprite-resizer/init)})))

(defn get-default-db [{:keys [palettes sprite]}]
  (let [sprite-size (or (when sprite
                          (sprite/get-size sprite))
                        {:width 16 :height 16})
        res-palettes (or palettes initial-palettes)
        primary-color (or (-> res-palettes first :colors first) "rgb(0,0,0)")
        secondary-color "rgb(255,0,0)"]
    (get-db {:sprite
             (or
              sprite
              (sprite/create {:size sprite-size
                              :layer (layer/create (get-layer-name :single 0))
                              :frame (frame/create initial-frame-duration)
                              :cel (->> (cel/create sprite-size)
                                        (cel/set-pixels (->> (for [x (range 0 (:width sprite-size))
                                                                   y (range 0 (:height sprite-size))]
                                                               [{:x x :y y} transparent-color])
                                                             (into {})))
                                        (cel/set-pixels
                                         {{:x 0 :y 0} secondary-color
                                          {:x 0 :y 1} transparent-color
                                          {:x 1 :y 1} secondary-color
                                          {:x 3 :y 3} secondary-color
                                          {:x 3 :y 4} secondary-color
                                          {:x 4 :y 3} secondary-color
                                          {:x 4 :y 4} secondary-color}))}))
             :palettes res-palettes
             :primary-color primary-color
             :secondary-color secondary-color})))
