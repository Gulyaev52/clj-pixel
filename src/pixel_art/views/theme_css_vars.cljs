(ns pixel-art.views.theme-css-vars
  (:require
   [pixel-art.views.ui-kit :refer [use-theme-token]]
   [react :as react])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def-func-component theme-css-vars []
  (let [t (use-theme-token)]
    (react/useLayoutEffect
     (fn []
       (let [style (.. js/document -documentElement -style)]
         (.setProperty style "--pixel-color-bg-container" (.-colorBgContainer t))
         (.setProperty style "--pixel-color-border" (.-colorBorder t))
         (.setProperty style "--pixel-color-primary-active" (.-colorPrimaryActive t))
         (.setProperty style "--pixel-color-text" (.-colorText t))
         (.setProperty style "--pixel-color-bg-base" (.-colorBgBase t))))
     (array t))
    [:<>]))
