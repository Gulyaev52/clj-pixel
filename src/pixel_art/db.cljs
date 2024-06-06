(ns pixel-art.db
  (:require [pixel-art.history :as history]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.onion-skin :as onion-skin]
            [pixel-art.palette :as palette]
            [pixel-art.sprite-preview :as preview]
            [pixel-art.tool.core :as tool]))

(def max-scale 80)

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-default-db [palette-local-storage-item]
  (let [sprite-size {:width 8 :height 8}
        sprite (->> (sprite/create {:size sprite-size})
                    (sprite/update-current-cel #(->> %
                                                     (cel/set-pixels (->> (for [x (range 0 (:width sprite-size))
                                                                                y (range 0 (:height sprite-size))]
                                                                            [{:x x :y y} "green"])
                                                                          (into {})))
                                                     (cel/set-pixels
                                                      {{:x 0 :y 0} "black"
                                                       {:x 0 :y 1} transparent-color
                                                       {:x 1 :y 1} "black"
                                                       {:x 3 :y 3} "black"
                                                       {:x 3 :y 4} "black"
                                                       {:x 4 :y 3} "black"
                                                       {:x 4 :y 4} "black"}))))
        viewport-size {:width 900 :height 700}
        scale max-scale
        canvas-size (update-vals sprite-size #(* % scale))
        drawing-container-size (update-vals canvas-size #(+ % 1500))]
    (-> {:size sprite-size
         :sprite sprite
         :tool (tool/init :pen)
         :tools-options (get-initial-options tool/options-specs)
         :primary-color "black"
         :secondary-color "red"
         :selection-manager {}
         :scale scale
         :viewport-size viewport-size
         :viewport-scroll {:x 700 :y 700}
         :drawing-container-size drawing-container-size
         :onion-skin (onion-skin/init)
         :history (history/init {:sprite sprite})
         :sprite-preview (preview/init)
         :pixels-grid-enabled true}
        (palette/init palette-local-storage-item))))
