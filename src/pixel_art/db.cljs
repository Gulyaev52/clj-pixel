(ns pixel-art.db
  (:require
   [pixel-art.drawing.initial-settings :refer [get-initial-drawing-settings]]
   [pixel-art.export-modal.events :as export-modal.events]
   [pixel-art.history :as history.events]
   [pixel-art.keyboard-shortcuts-modal.events :as keyboard-shortcuts-modal.events]
   [pixel-art.new-project-modal.events :as new-project-modal.events]
   [pixel-art.onion-skin.events :as onion-skin.events]
   [pixel-art.sprite-preview.events :as sprite-preview.events]
   [pixel-art.sprite-resizer.events :as sprite-resizer.events]
   [pixel-art.tool.core :as tool]
   [sc.api]))

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(defn get-db [{:keys [sprite tool-type primary-color palettes secondary-color pixels-grid-enabled new-project-modal-opened]} viewport-size]
  (merge
   {:size (:size sprite)
    :preview nil
    :visual-effects nil
    :pixels-grid-enabled (if (some? pixels-grid-enabled) pixels-grid-enabled true)
    :new-project-modal (new-project-modal.events/init new-project-modal-opened)
    :sprite sprite
    :tool (tool/init (or tool-type :pen))
    :tools-options (get-initial-options tool/options-specs)
    :primary-color primary-color
    :secondary-color secondary-color
    :selection-manager {}
    :onion-skin (onion-skin.events/init)
    :history (history.events/init {:sprite sprite})
    :sprite-preview (sprite-preview.events/init)
    :palettes palettes
    :keyboard-shortcuts-modal (keyboard-shortcuts-modal.events/init)
    :export-modal (export-modal.events/init)
    :sprite-resizer (sprite-resizer.events/init)
    :last-saved-history-idx 0} ;; TODO: Find a better solutiion. This approach is not reliabe on 100% since history has max size.
   (get-initial-drawing-settings (:size sprite) viewport-size)))