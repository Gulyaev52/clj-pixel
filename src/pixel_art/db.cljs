(ns pixel-art.db
  (:require
   [pixel-art.model.frame :as frame]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.tool.core :as tool]
   [pixel-art.history :as history]
   [pixel-art.preview :as preview]))

(defn get-initial-options [m]
  (-> m
      (update-vals #(->> %
                         (map (fn [{:keys [field initial-value]}] [field initial-value]))
                         (into {})))))

(def default-db
  (let [size {:width 8 :height 8}
        frame (->> (frame/create size)
                   (frame/set-pixels (->> (for [x (range 0 8)
                                                y (range 0 8)]
                                            [{:x x :y y} "green"])
                                          (into {})))
                   (frame/set-pixels
                    {{:x 0 :y 0} "black"
                     {:x 0 :y 1} frame/transparent-color
                     {:x 1 :y 1} "black"}))
        sprite (->> (sprite/create {:width 8 :height 8})
                    (sprite/add-frame frame))]
    {:size size
     :sprite sprite
     :tool (tool/init :pen)
     :tools-options (get-initial-options tool/options-specs)
     :color "black"
     :selection-manager {}
     :scale 40
     :history (history/init {:sprite sprite})
     :preview-settings (preview/init)
     :pixels-grid-enabled true}))
