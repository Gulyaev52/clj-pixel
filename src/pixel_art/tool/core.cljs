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

(def tools
  [pen/tool
   eraser/tool
   bucket/tool
   color-picker/tool
   rectangle/tool
   circle/tool
   line/tool
   rectangle-selection/tool
   shape-selection/tool
   shading/tool])

(def types (map :type tools))

(def tools-m (zipmap types tools))

(def options-specs (zipmap types
                           (map :options-spec tools)))

(defn init [tool-type]
  (let [init-fn (:init (tools-m tool-type))]
    (init-fn)))

(defn get-events-handlers [db]
  ((:get-events-handlers (tools-m (-> db :tool :type))) db))
