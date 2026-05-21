(ns pixel-art.re-pressed.core
  (:require
   [re-frame.core :as rf]
   [pixel-art.re-pressed.impl]))

;; https://github.com/gadfly361/re-pressed
;; only change from the source library is that events are triggered when input[type="range"] or input["checkbox"] is focused


(rf/reg-event-fx
 ::add-keyboard-event-listener
 ;; event-type can be:
 ;; "keydown",
 ;; "keypress", or
 ;; "keyup"
 (fn [_ [_ event-type & {:as args-map}]]
   {::keyboard-event {:event-type event-type :arguments args-map}}))


(rf/reg-event-fx
 ::set-keydown-rules
 (fn [{:keys [db]}
      [_ {:keys [event-keys
                 clear-keys
                 always-listen-keys
                 prevent-default-keys]
          :as   opts}]]
   {:db (-> db
            (assoc-in [::keydown :keys] nil)
            (assoc-in [::keydown :event-keys] event-keys)
            (assoc-in [::keydown :clear-keys] clear-keys)
            (assoc-in [::keydown :always-listen-keys] always-listen-keys)
            (assoc-in [::keydown :prevent-default-keys] prevent-default-keys))}))


(rf/reg-event-fx
 ::set-keypress-rules
 (fn [{:keys [db]}
      [_ {:keys [event-keys
                 clear-keys
                 always-listen-keys]
          :as   opts}]]
   {:db (-> db
            (assoc-in [::keypress :keys] nil)
            (assoc-in [::keypress :event-keys] event-keys)
            (assoc-in [::keypress :clear-keys] clear-keys)
            (assoc-in [::keypress :always-listen-keys] always-listen-keys))}))


(rf/reg-event-fx
 ::set-keyup-rules
 (fn [{:keys [db]}
      [_ {:keys [event-keys
                 clear-keys
                 always-listen-keys]
          :as   opts}]]
   {:db (-> db
            (assoc-in [::keyup :keys] nil)
            (assoc-in [::keyup :event-keys] event-keys)
            (assoc-in [::keyup :clear-keys] clear-keys)
            (assoc-in [::keyup :always-listen-keys] always-listen-keys))}))