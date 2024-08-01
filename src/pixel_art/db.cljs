(ns pixel-art.db
  (:require [pixel-art.history :as history]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.onion-skin :as onion-skin]
            [pixel-art.palette :as palette]
            [pixel-art.sprite-preview :as preview]
            [pixel-art.tool.core :as tool]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.frame :as frame]))

(def max-scale 80)

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-layer-name [type layers-count]
  (str (if (= type :group) "Group " "Layer ") (inc layers-count)))

(def initial-frame-duration 100)

(defn get-db [{:keys [sprite palettes primary-color secondary-color]}]
  (let [viewport-size {:width 900 :height 700}
        scale max-scale
        canvas-size (update-vals (sprite/get-size sprite) #(* % scale))
        drawing-container-size (update-vals canvas-size #(+ % 1500))]
    (-> {:size (sprite/get-size sprite) ;; todo: size можно получить из спрайта
         :sprite sprite
         :tool (tool/init :pen)
         :tools-options (get-initial-options tool/options-specs)
         :primary-color primary-color ;; todo: а тут точно так а не индексами? на инит может быть ерунда
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
         :palettes (palette/init palettes)})))

(defn get-default-db [{:keys [palettes initial-pixels-map]}]
  (let [sprite-size {:width 8 :height 8}]
    (get-db {:sprite
             (sprite/create {:size sprite-size
                             :layer (layer/create (get-layer-name :single 0) nil)
                             :frame (frame/create initial-frame-duration)
                             :cel (->> (cel/create sprite-size)
                                       (cel/set-pixels (->> (for [x (range 0 (:width sprite-size))
                                                                  y (range 0 (:height sprite-size))]
                                                              [{:x x :y y} nil])
                                                            (into {})))
                                       (cel/set-pixels
                                        (or initial-pixels-map
                                            {{:x 0 :y 0} "black"
                                             {:x 0 :y 1} transparent-color
                                             {:x 1 :y 1} "black"
                                             {:x 3 :y 3} "black"
                                             {:x 3 :y 4} "black"
                                             {:x 4 :y 3} "black"
                                             {:x 4 :y 4} "black"})))})
             :palettes palettes
             :primary-color "black"
             :secondary-color "red"})))
