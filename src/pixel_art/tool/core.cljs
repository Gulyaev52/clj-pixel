(ns pixel-art.tool.core
  (:require [pixel-art.tool.pen :as pen]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [pixel-art.tool.shape-select :as shape-select]))

;; todo: исп полиморфизм?

(def types
  [:pen :rectangle :rectangle-select :shape-select])

(defn init [tool-type]
  (case tool-type
    :pen (pen/init)
    :rectangle (rectangle/init)
    :rectangle-select (rectangle-select/init)
    :shape-select (shape-select/init)))

(def options-specs
  {:pen pen/options-spec
   :rectangle rectangle/options-spec
   :rectangle-select rectangle-select/options-spec
   :shape-select shape-select/options-spec})

(defn handle-mouse-event [db event]
  (case (-> db :tool :type)
    :pen (pen/handle-mouse-event db event)
    :rectangle (rectangle/handle-mouse-event db event)
    :rectangle-select (rectangle-select/handle-mouse-event db event)
    :shape-select (shape-select/handle-mouse-event db event)))
