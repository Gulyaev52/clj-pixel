(ns pixel-art.tool.shading
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.simple-pen :as simple-pen]
   [pixel-art.tool.utils :refer [get-tool-options
                                 resize-pixel]]))

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
    :get-points
    (fn [db event]
      (let [{:keys [pixel-size amount lighten]} (get-tool-options db)
            {:keys [sprite]} db
            current-cel (sprite/get-current-cel sprite)]
        (->> (resize-pixel (:pos event) pixel-size)
             (keep (fn [pos]
                     (let [color (cel/get-pixel pos current-cel)]
                       (when-not (= color color/transparent-color-int)
                         (let [tcolor (color/->tinycolor color)]
                           (if lighten
                             (. tcolor (lighten amount))
                             (. tcolor (darken amount)))
                           [pos (color/int tcolor)]))))))))}))
