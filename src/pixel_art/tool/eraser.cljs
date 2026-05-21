(ns pixel-art.tool.eraser
  (:require
   [pixel-art.model.color :refer [transparent-color-int]]
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.simple-pen :as simple-pen]))

(def tool
  (simple-pen/make
   {:type :eraser
    :options-spec [options-spec/pixel-size]
    :get-color (fn [] transparent-color-int)}))
