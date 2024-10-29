(ns pixel-art.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [pixel-art.events :as events]
   [pixel-art.events.event-collector]
   [pixel-art.re-pressed.core :as rp]
   [pixel-art.views :as views]
   [pixel-art.config :as config]))


(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/app]
                 root-el)))

(defn ^:dev/after-load update-canvas []
  (re-frame/dispatch [::events/initialize-canvas]))

(defn init []
  (re-frame/dispatch-sync [::events/start-app])
  (re-frame/dispatch-sync [::rp/add-keyboard-event-listener "keydown"])
  (dev-setup)
  (mount-root))
