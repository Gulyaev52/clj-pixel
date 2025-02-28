(ns pixel-art.tool.line
  (:require
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color
                                 get-preview-from-current-cel get-tool-options
                                 resize-pixel]]
   [pixel-art.utils.geometry :as geometry]
   [pixel-art.model.preview :as preview]))

(def tool
  (shape/make
   {:type :line
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :straight :label "Straight"})]
    :draw
    (fn draw [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            current-color (get-current-color db event)
            {:keys [pixel-size straight]} (get-tool-options db)
            points (->> (if straight
                          (geometry/get-uniform-line-pixels initial-mouse-down-pos (:pos event))
                          (geometry/get-line-pixels initial-mouse-down-pos (:pos event)))
                        (mapcat #(resize-pixel % pixel-size)) ;; todo: resize точно правильный? https://github.com/piskelapp/piskel/blob/21b8bdd0f3602c455e89f25fb337068fd9ea3a35/src/js/tools/drawing/Stroke.js#L101
                        )
            preview (get-preview-from-current-cel db)]
        (doseq [{:keys [x y]} points]
          (preview/set-color! preview x y current-color))
        preview))}))
