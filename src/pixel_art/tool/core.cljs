(ns pixel-art.tool.core
  (:require [pixel-art.tool.pen :as pen]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.tool.rectangle-select :as rectangle-select]))

(def tools-options-specs
  {:pen pen/options-spec
   :rectangle rectangle/options-spec
   :rectangle-select rectangle-select/options-spec})
