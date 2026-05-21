(ns pixel-art.tool.shading
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.simple-pen :as simple-pen]
   [pixel-art.tool.utils :refer [get-current-cel get-tool-options]]))

(defn get-color [db {:keys [pos]}]
  (let [{:keys [amount lighten]} (get-tool-options db)
        current-cel (get-current-cel db)
        color (cel/get-pixel pos current-cel)]
    (if (not= color color/transparent-color-int)
      (let [tcolor (color/->tinycolor color)]
        (if lighten
          (. tcolor (lighten amount))
          (. tcolor (darken amount)))
        (color/int tcolor))
      color)))

(def tool
  (simple-pen/make
   {:type :shading
    :options-spec [options-spec/pixel-size
                   (options-spec/make-slider {:field :amount
                                              :label "Amount"
                                              :initial-value 6
                                              :min 1
                                              :max 100})
                   (options-spec/make-checkbox {:field :lighten
                                                :label "Lighten"})]
    :get-color #'get-color}))
