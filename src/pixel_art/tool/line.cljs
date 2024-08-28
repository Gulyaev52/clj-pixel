(ns pixel-art.tool.line
  (:require [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-current-color get-tool-options
                                          resize-pixel]]
            [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :line})

(def options-spec
  [{:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}
   {:type :checkbox
    :field :straight
    :initial-value false
    :label "Straight"}])

(defn- get-line-image [db event]
  (let [{:keys [initial-mouse-down-pos]} db
        current-color (get-current-color db event)
        {:keys [pixel-size straight]} (get-tool-options db)
        line-points (if straight
                      (geometry/get-uniform-line-pixels initial-mouse-down-pos (:pos event))
                      (geometry/get-line-pixels initial-mouse-down-pos (:pos event)))]
    (->> line-points
         (mapcat #(resize-pixel % pixel-size)) ;; todo: resize точно правильный? https://github.com/piskelapp/piskel/blob/21b8bdd0f3602c455e89f25fb337068fd9ea3a35/src/js/tools/drawing/Stroke.js#L101
         (map (fn [p] [p current-color])))))

(defn handle-mouse-event [db event]
  (let [{:keys [user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      {:db db
       :fx [[:clear-preview]
            [:draw-preview (get-line-image db event)]]}

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels points]]})

      (= :mouse-up (:type event))
      (let [rectangle-image (get-line-image db event)]
        (commit-changes-and-init-tool db rectangle-image (init))))))
