(ns pixel-art.tool.pen
  (:require
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.simple-pen :as simple-pen]
   [pixel-art.tool.utils :refer [get-current-color]]))

(def tool
  (simple-pen/make
   {:type :pen
    :options-spec [options-spec/pixel-size]
    :get-color get-current-color}))
