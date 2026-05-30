(ns pixel-art.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [pixel-art.events :as events]
   [pixel-art.utils.event-collector]
   [pixel-art.views :as views]))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/app]
                 root-el)))

(defn init []
  (re-frame/dispatch-sync [::events/start-app])
  (mount-root))
