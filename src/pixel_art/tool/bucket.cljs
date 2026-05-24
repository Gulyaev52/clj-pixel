(ns pixel-art.tool.bucket
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.preview :as preview]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.utils :refer [commit-preview-and-init-tool
                                 get-current-color
                                 get-preview-from-current-cel get-tool-options
                                 with-highlight-cel-under-cursor]]
   [pixel-art.utils.geometry :as geometry]))

(defn- init [] {:type :bucket})

(def tool
  {:type :bucket
   :init init
   :options-spec [(options-spec/make-checkbox {:field :same-color
                                               :label "All the same color"})]
   :get-events-handlers
   (fn []
     (with-highlight-cel-under-cursor
       {:mouse-down
        (fn [db event]
          (let [{:keys [sprite initial-mouse-down-pos]} db
                {:keys [same-color]} (get-tool-options db)
                current-color (get-current-color db event)
                current-cel (sprite/get-current-cel sprite)
                target-color (cel/get-pixel initial-mouse-down-pos current-cel)
                preview (get-preview-from-current-cel db)]
            (if same-color
              (. (:pixels current-cel) (forEach (fn [color idx]
                                                  (when (= color target-color)
                                                    (preview/set-color! preview idx current-color)))))
              (geometry/visit-connected-pixels initial-mouse-down-pos
                                               (fn [x y]
                                                 (when (= (preview/get-color preview x y) target-color)
                                                   (preview/set-color! preview x y current-color)
                                                   true))))
            (commit-preview-and-init-tool db preview (init))))}))})
