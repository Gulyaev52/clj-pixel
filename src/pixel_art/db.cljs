(ns pixel-art.db
  (:require
   [pixel-art.export.events :as export]
   [pixel-art.history :as history]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.new-project-modal.events :as new-project-modal]
   [pixel-art.keyboard-shortcuts-modal.events :as keyboard-shortcuts-modal]
   [pixel-art.onion-skin :as onion-skin]
   [pixel-art.palette :as palette]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.sprite-preview :as preview]
   [pixel-art.sprite-resizer.events :as sprite-resizer]
   [pixel-art.tool.core :as tool]
   [sc.api]))

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-db [{:keys [sprite palettes primary-color secondary-color pixels-grid-enabled new-project-modal-opened]} viewport-size]
  (merge
   {:viewport-size viewport-size
    :size (sprite/get-size sprite)
    :new-project-modal (new-project-modal/init new-project-modal-opened)
    :scale project-settings/min-scale
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
    :keyboard-shortcuts-modal (keyboard-shortcuts-modal/init)
    :export (export/init)
    :sprite-resizer (sprite-resizer/init)}
   (project-settings/get-initial-drawing-settings (:size sprite) viewport-size)))
