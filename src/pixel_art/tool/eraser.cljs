(ns pixel-art.tool.eraser
  (:require
   [pixel-art.model.color :refer [transparent-color-int]]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.simple-pen :as simple-pen]
   [pixel-art.tool.utils :refer [get-tool-options
                                 resize-pixel]]))

(defn- draw [db event]
  (let [{:keys [pixel-size]} (get-tool-options db)]
    (->> (resize-pixel (:pos event) pixel-size)
         (map (fn [p] [p transparent-color-int])))))

(def tool
  (simple-pen/make
   {:type :eraser
    :options-spec [options-spec/pixel-size]
    :draw draw}))
