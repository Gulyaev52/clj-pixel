(ns pixel-art.utils.fx
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-fx
 :show-alert
 (fn [message]
   (js/alert message)))

(re-frame/reg-fx
 :download-file
 (fn [{:keys [file-name content content-type]}]
   (let [data-blob (if (= content-type :json) ;; todo: remove json
                     (js/Blob. #js [content] #js {:type "application/json"})
                     content)
         link (.createElement js/document "a")]
     (set! (.-href link) (.createObjectURL js/URL data-blob))
     (.setAttribute link "download" file-name)
     (.appendChild (.-body js/document) link)
     (.click link)
     (.removeChild (.-body js/document) link))))

(defn- now [] (.getTime (js/Date.)))

(def registered-keys (atom nil))

(defn- dispatch-if-not-superceded [{:keys [key delay event time-received]}]
  (when (= time-received (get @registered-keys key))
    ;; no new events on this key!
    (re-frame/dispatch event)))

(defn- dispatch-later [{:keys [delay] :as debounce}]
  (js/setTimeout
   (fn [] (dispatch-if-not-superceded debounce))
   delay))

;; https://github.com/johnswanson/re-frame-debounce-fx/tree/develop

(re-frame/reg-fx
 :dispatch-debounce
 (fn dispatch-debounce [debounce]
   (let [ts (now)]
     (swap! registered-keys assoc (:key debounce) ts)
     (dispatch-later (assoc debounce :time-received ts)))))
