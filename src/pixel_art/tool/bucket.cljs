(ns pixel-art.tool.bucket
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                 get-current-color get-tool-options]]
   [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :bucket})

(def options-spec
  [{:type :checkbox
    :field :same-color
    :initial-value false
    :label "All the same color"}])

(defn handle-mouse-event [db event]
  (cond
    (= (:type event) :mouse-down)
    (let [{:keys [sprite initial-mouse-down-pos]} db
          {:keys [same-color]} (get-tool-options db)
          current-color (get-current-color db event)
          current-cel (sprite/get-current-cel sprite)
          target-color (cel/get-pixel initial-mouse-down-pos current-cel)
          points (->> (if same-color
                        (->> current-cel
                             cel/pixels->coll
                             (filter (fn [[_ color]] (= color target-color)))
                             (map first))
                        (geometry/flood-fill initial-mouse-down-pos
                                             (:size current-cel)
                                             (:pixels current-cel)
                                             target-color))
                      (mapv (fn [p] [p current-color])))]
      (commit-changes-and-init-tool db points (init)))

    (and (= (:type event) :mouse-move) (not (:user-is-drawing db)))
    {:db db
     :fx [[:clear-preview]
          [:highlight-pixels [(:pos event)]]]}

    :else {:db db}))
