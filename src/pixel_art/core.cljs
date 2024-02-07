(ns pixel-art.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [pixel-art.events :as events]
   [pixel-art.events.event-collector]
   [re-pressed.core :as rp]
   [pixel-art.views :as views]
   [pixel-art.config :as config]))


(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel]
                 root-el
                 (fn [] (re-frame/dispatch [::events/initialize-canvas])))))

(defn init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch-sync [::rp/add-keyboard-event-listener "keydown"])
  (dev-setup)
  (mount-root))
