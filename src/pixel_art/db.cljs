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

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-db [{:keys [sprite palettes primary-color initialized-canvas secondary-color pixels-grid-enabled new-project-modal-opened]}]
  {:initialized-canvas initialized-canvas
   :size (sprite/get-size sprite)
   :new-project-modal (new-project-modal/init new-project-modal-opened)
   :sprite sprite
   :tool (tool/init :pen)
   :tools-options (get-initial-options tool/options-specs)
   :primary-color primary-color
   :secondary-color secondary-color
   :selection-manager {}
   :onion-skin (onion-skin/init)
   :history (history/init {:sprite sprite})
   :sprite-preview (preview/init)
   :pixels-grid-enabled (if (some? pixels-grid-enabled) pixels-grid-enabled true)
   :palettes (palette/init palettes)
   :keyboard-shortcuts-modal-opened false
   :export (export/init)
   :sprite-resizer (sprite-resizer/init)})
