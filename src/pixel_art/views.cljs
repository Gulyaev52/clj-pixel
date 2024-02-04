(ns pixel-art.views
  (:require [pixel-art.subs :as subs]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div {}
     "adfadf"
     [:canvas {:id "tutorial"
               :style {:border "1px solid black" :position "absolute" :left 0}
               :width "150"
               :height "150"}]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))