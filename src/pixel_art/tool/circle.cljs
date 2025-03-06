(ns pixel-art.tool.circle
  (:require
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color
                                 get-preview-from-current-cel get-tool-options]]
   [pixel-art.utils.geometry :as geometry]
   [pixel-art.model.preview :as preview]))

(def tool
  (shape/make
   {:type :circle
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :keep-ratio :label "Keep ration"})
                   #_(options-spec/make-checkbox {:field :fill :label "Fill"})] ;; todo: implement
    :draw
    (fn [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            current-color (get-current-color db event)
            {:keys [pixel-size keep-ratio]} (get-tool-options db)
            current-pos (if keep-ratio
                          (geometry/get-scaled-points initial-mouse-down-pos (:pos event))
                          (:pos event))
            preview (get-preview-from-current-cel db)]
        (geometry/get-circle-pixels initial-mouse-down-pos
                                    current-pos
                                    pixel-size
                                    (fn [x y]
                                      (preview/set-color! preview x y current-color)))

        preview))}))
