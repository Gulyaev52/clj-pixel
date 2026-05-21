(ns pixel-art.sprite-preview.views
  (:require
   [pixel-art.sprite-preview.events :as events]
   [pixel-art.subs :as common-subs]
   [pixel-art.sprite-preview.subs :as subs]
   [pixel-art.views.preview :refer [preview-image]]
   [re-frame.core :as re-frame]
   [react :as react])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def-func-component sprite-preview-modal-component []
  (let [{:keys [size displayed-frame-idx frame-imgs]} @(re-frame/subscribe [::subs/sprite-preview])
        sprite-size @(re-frame/subscribe [::common-subs/sprite-size])
        frame-img (or (get frame-imgs displayed-frame-idx nil) (get frame-imgs 0))
        image-size (case size
                     :1x sprite-size
                     :2x (update-vals sprite-size #(* % 2))
                     :4x (update-vals sprite-size #(* % 4))
                     :default {:width 512 :height 512})
        _ (react/useEffect (fn []
                             (.. js/document (addEventListener "keydown" (fn [e]
                                                                           (when (= (.. e -code) "Escape")
                                                                             (re-frame/dispatch [::events/close]))))))
                           (array))]
    [:div {:style {:position "fixed"
                   :display "flex"
                   :z-index 1000
                   :alignItems "center"
                   :justifyContent "center"
                   :left 0
                   :right 0
                   :bottom 0
                   :top 0
                   :backgroundColor "rgba(37, 37, 37, 0.9)"}}
     [preview-image frame-img {:height (:height image-size)}]]))

(defn sprite-preview-modal []
  (let [opened (:opened @(re-frame/subscribe [::subs/sprite-preview]))]
    (when opened
      [sprite-preview-modal-component])))
