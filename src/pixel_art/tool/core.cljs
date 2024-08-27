(ns pixel-art.tool.core
  (:require [pixel-art.tool.bucket :as bucket]
            [pixel-art.tool.circle :as circle]
            [pixel-art.tool.color-picker :as color-picker]
            [pixel-art.tool.eraser :as eraser]
            [pixel-art.tool.pen :as pen]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [pixel-art.tool.shape-select :as shape-select]))

;; todo: исп полиморфизм?

(def types
  [:pen :bucket :eraser :color-picker :rectangle :circle :rectangle-select :shape-select])

(defn init [tool-type]
  ((case tool-type
     :pen pen/init
     :eraser eraser/init
     :color-picker color-picker/init
     :rectangle rectangle/init
     :circle circle/init
     :rectangle-select rectangle-select/init
     :shape-select shape-select/init
     :bucket bucket/init)))

(def options-specs
  {:pen pen/options-spec
   :eraser eraser/options-spec
   :color-picker color-picker/options-spec
   :rectangle rectangle/options-spec
   :circle circle/options-spec
   :rectangle-select rectangle-select/options-spec
   :shape-select shape-select/options-spec
   :bucket bucket/options-spec})

(defn handle-mouse-event [db event]
  ((case (-> db :tool :type)
     :pen pen/handle-mouse-event
     :eraser eraser/handle-mouse-event
     :color-picker color-picker/handle-mouse-event
     :rectangle rectangle/handle-mouse-event
     :circle circle/handle-mouse-event
     :rectangle-select rectangle-select/handle-mouse-event
     :shape-select shape-select/handle-mouse-event
     :bucket bucket/handle-mouse-event)
   db event))
