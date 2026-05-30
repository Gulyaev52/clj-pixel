(ns pixel-art.utils.fx
  (:require
   ["antd" :as antd]
   [pixel-art.utils.fx.local-storage]
   [re-frame.core :as re-frame]))

(re-frame/reg-fx
 :show-alert
 (fn [message]
   (js/alert message)))

(re-frame/reg-event-fx
 :show-notification
 (fn [_ [_ config]]
   {:fx [[:show-notification config]]}))

(re-frame/reg-fx
 :show-notification
 (fn [config]
   (.open antd/notification (clj->js (merge config
                                            {"transitionName" ""})))))

(re-frame/reg-fx
 :download-file
 (fn [{:keys [file-name content content-type]}]
   (let [data-blob (if (= content-type :json)
                     (js/Blob. #js [content] #js {:type "application/json"})
                     content)
         link (.createElement js/document "a")]
     (set! (.-href link) (.createObjectURL js/URL data-blob))
     (.setAttribute link "download" file-name)
     (.appendChild (.-body js/document) link)
     (.click link)
     (.removeChild (.-body js/document) link))))

;; https://github.com/district0x/re-frame-interval-fx/blob/master/src/district0x/re_frame/interval_fx.cljs

(def registered-keys (atom nil))

(re-frame/reg-fx
 :dispatch-interval
 (fn [{:keys [:dispatch :ms :id]}]
   (let [interval-id (js/setInterval #(re-frame/dispatch dispatch) ms)]
     (swap! registered-keys assoc id interval-id))))

(re-frame/reg-fx
 :clear-interval
 (fn [{:keys [:id]}]
   (when-let [interval-id (get @registered-keys id)]
     (js/clearInterval interval-id)
     (swap! registered-keys dissoc id))))

;;

(re-frame/reg-fx
 :confirm
 (fn [{:keys [message on-ok on-cancel]}]
   (if (js/confirm message)
     (re-frame/dispatch on-ok)
     (when on-cancel
       (re-frame/dispatch on-cancel)))))