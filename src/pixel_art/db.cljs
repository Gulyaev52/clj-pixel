(ns pixel-art.db
  (:require [pixel-art.export :as export]
            [pixel-art.history :as history]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.new-project-modal :as new-project-modal]
            [pixel-art.onion-skin :as onion-skin]
            [pixel-art.palette :as palette]
            [pixel-art.sprite-preview :as preview]
            [pixel-art.sprite-resizer :as sprite-resizer]
            [pixel-art.tool.core :as tool]
            [sc.api]))

(def max-scale 80)

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-db [{:keys [sprite palettes primary-color secondary-color pixels-grid-enabled new-project-modal-opened]}]
  (let [viewport-size {:width 900 :height 700}
        scale max-scale
        canvas-size (update-vals (sprite/get-size sprite) #(* % scale))
        drawing-container-size (update-vals canvas-size #(+ % 1500))]
    (-> {:size (sprite/get-size sprite)
         :new-project-modal (new-project-modal/init new-project-modal-opened)
         :sprite sprite
         :tool (tool/init :pen)
         :tools-options (get-initial-options tool/options-specs)
         :primary-color primary-color
         :secondary-color secondary-color
         :selection-manager {}
         :scale scale
         :viewport-size viewport-size
         :viewport-scroll {:x 700 :y 700}
         :drawing-container-size drawing-container-size
         :onion-skin (onion-skin/init)
         :history (history/init {:sprite sprite})
         :sprite-preview (preview/init)
         :pixels-grid-enabled (if (some? pixels-grid-enabled) pixels-grid-enabled true)
         :palettes (palette/init palettes)
         :keyboard-shortcuts-modal-opened false
         :export (export/init)
         :sprite-resizer (sprite-resizer/init)})))
