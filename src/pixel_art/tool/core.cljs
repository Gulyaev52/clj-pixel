(ns pixel-art.tool.core
  (:require [pixel-art.tool.bucket :as bucket]
            [pixel-art.tool.circle :as circle]
            [pixel-art.tool.color-picker :as color-picker]
            [pixel-art.tool.eraser :as eraser]
            [pixel-art.tool.line :as line]
            [pixel-art.tool.pen :as pen]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.tool.rectangle-selection :as rectangle-selection]
            [pixel-art.tool.shading :as shading]
            [pixel-art.tool.shape-selection :as shape-selection]))

;; todo: исп полиморфизм?

(def types
  [:pen :eraser :bucket :color-picker :rectangle :circle :line :rectangle-selection :shape-selection :shading])

(defn init [tool-type]
  ((case tool-type
     :pen pen/init
     :eraser eraser/init
     :color-picker color-picker/init
     :rectangle (:init rectangle/tool)
     :circle (:init circle/tool)
     :line (:init line/tool)
     :rectangle-selection rectangle-selection/init
     :shape-selection shape-selection/init
     :bucket bucket/init
     :shading shading/init)))

(def options-specs
  {:pen pen/options-spec
   :eraser eraser/options-spec
   :color-picker color-picker/options-spec
   :rectangle (:options-spec rectangle/tool)
   :circle (:options-spec circle/tool)
   :line (:options-spec line/tool)
   :rectangle-selection rectangle-selection/options-spec
   :shape-selection shape-selection/options-spec
   :bucket bucket/options-spec
   :shading shading/options-spec})

(defn handle-mouse-event [db event]
  ((case (-> db :tool :type)
     :pen pen/handle-mouse-event
     :eraser eraser/handle-mouse-event
     :color-picker color-picker/handle-mouse-event
     :rectangle (:handle-mouse-event rectangle/tool)
     :line (:handle-mouse-event line/tool)
     :circle (:handle-mouse-event circle/tool)
     :rectangle-selection rectangle-selection/handle-mouse-event
     :shape-selection shape-selection/handle-mouse-event
     :bucket bucket/handle-mouse-event
     :shading shading/handle-mouse-event)
   db event))
