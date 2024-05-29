(ns pixel-art.db
  (:require [pixel-art.history :as history]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.onion-skin :as onion-skin]
            [pixel-art.palette :as palette]
            [pixel-art.sprite-preview :as preview]
            [pixel-art.tool.core :as tool]
            [sc.api :as api]))

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-default-db [palette-local-storage-item]
  (let [size {:width 64 :height 64}
        frame (->> (frame/create size)
                   (frame/set-pixels (->> (for [x (range 0 (:width size))
                                                y (range 0 (:height size))]
                                            [{:x x :y y} "green"])
                                          (into {})))
                   (frame/set-pixels
                    {{:x 0 :y 0} "black"
                     {:x 0 :y 1} frame/transparent-color
                     {:x 1 :y 1} "black"
                     {:x 3 :y 3} "black"
                     {:x 3 :y 4} "black"
                     {:x 4 :y 3} "black"
                     {:x 4 :y 4} "black"}))
        sprite (->> (sprite/create size)
                    (sprite/add-frame frame))
        viewport-size {:width 900 :height 700}
        scale 80
        canvas-size (update-vals size #(* % scale))
        drawing-container-size (update-vals canvas-size #(+ % 1500))]
    (-> {:size size
         :sprite sprite
         :tool (tool/init :pen)
         :tools-options (get-initial-options tool/options-specs)
         :primary-color "black"
         :secondary-color "red"
         :selection-manager {}
         :scale scale
         :canvas-size canvas-size
         :viewport-size viewport-size
         :viewport-scroll {:x 0
                           :y 0}
         :drawing-container-size drawing-container-size
         :onion-skin (onion-skin/init)
         :history (history/init {:sprite sprite})
         :sprite-preview (preview/init)
         :pixels-grid-enabled true}
        (palette/init palette-local-storage-item))))
