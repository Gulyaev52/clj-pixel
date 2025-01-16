(ns pixel-art.sprite-resizer.views
  (:require
   [pixel-art.project-settings :as project-settings]
   [pixel-art.sprite-resizer.subs :as subs]
   [pixel-art.sprite-resizer.events :as events]
   [pixel-art.views.preview :refer [previews-container previews-grid-items]]
   [pixel-art.views.ui-kit :refer [checkbox form form-item input-number modal
                                   use-theme-token]]
   [re-frame.core :as re-frame])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def-func-component anchor [settings]
  (let [theme-token (use-theme-token)]
    [:div {:style {:display :grid
                   :grid-template-columns "min-content min-content min-content"
                   :gap "1px"
                   :opacity (when (:resize-content settings) "0.6")}}
     (for [y [:top :center :bottom]
           x [:left :center :right]]
       ^{:key (str y "-y-" x "-x")}
       [:div {:title (str (name y) "/" (name x))
              :style {:border-radius "4px"
                      :width "24px"
                      :height "24px"
                      :background-color (if (and (not (:resize-content settings))
                                                 (= {:x x :y y} (:anchor settings)))
                                          (.-colorPrimaryActive theme-token)
                                          "#444")}
              :on-click (fn []
                          (re-frame/dispatch [::events/set-settings-option :anchor {:x x :y y}]))}])]))

(defn sprite-resizer-modal []
  (when @(re-frame/subscribe [::subs/opened])
    (let [settings @(re-frame/subscribe [::subs/settings])
          previews @(re-frame/subscribe [::subs/previews])]
      [modal {:title "Resize canvas"
              :size :sm
              :on-cancel (fn []
                           (re-frame/dispatch [::events/set-opened false]))
              :ok-text "Resize"
              :on-ok (fn []
                       (re-frame/dispatch [::events/resize]))}
       [form
        [form-item {:label "Width"
                    :control [input-number {:value (-> settings :target-size :width)
                                            :type "number"
                                            :max project-settings/max-sprite-dim
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-settings-option
                                                                           :target-size
                                                                           (assoc (:target-size settings) :width value)]))}]}]
        [form-item {:label "Height"
                    :control [input-number {:value (-> settings :target-size :height)
                                            :type "number"
                                            :max project-settings/max-sprite-dim
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-settings-option
                                                                           :target-size
                                                                           (assoc (:target-size settings) :height value)]))}]}]
        [form-item {:label "Resize contents"
                    :control [checkbox {:value (:resize-content settings)
                                        :on-change (fn [value]
                                                     (re-frame/dispatch [::events/set-settings-option :resize-content value]))}]}]
        [form-item {:label "Anchor"
                    :control [anchor settings]}]
        [previews-container {}
         [previews-grid-items previews]]]])))
