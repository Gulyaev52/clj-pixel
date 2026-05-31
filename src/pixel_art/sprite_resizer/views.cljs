(ns pixel-art.sprite-resizer.views
  (:require
   [pixel-art.project-config :as project-config]
   [pixel-art.sprite-resizer.subs :as subs]
   [pixel-art.sprite-resizer.events :as events]
   [pixel-art.views.preview :refer [previews-container previews-grid-items]]
   [pixel-art.views.ui-kit :refer [checkbox form form-item input-number modal]]
   [re-frame.core :as re-frame]
   [shadow.css :refer (css)])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def-func-component anchor [settings]
  [:div {:class (css {:display "grid"
                      :grid-template-columns "min-content min-content min-content"
                      :gap "1px"})
         :style {:opacity (when (:resize-content settings) "0.6")}}
   (for [y [:top :center :bottom]
         x [:left :center :right]]
     ^{:key (str y "-y-" x "-x")}
     [:div {:data-testid (str "anchor-" (name y) "-" (name x))
            :title (str (name y) "/" (name x))
            :class [(css {:border-radius "4px"
                          :width "24px"
                          :height "24px"
                          :background-color "#444"})
                    (when (and (not (:resize-content settings))
                               (= {:x x :y y} (:anchor settings)))
                      (css {:background-color "var(--pixel-color-primary-active)"}))]
            :on-click (fn []
                        (re-frame/dispatch [::events/set-settings-option :anchor {:x x :y y}]))}])])

(defn sprite-resizer-modal []
  (when @(re-frame/subscribe [::subs/opened])
    (let [settings @(re-frame/subscribe [::subs/settings])
          previews @(re-frame/subscribe [::subs/previews])]
      [modal {:title "Resize canvas"
              :size :sm
              :ok-data-testid "btn-resize"
              :on-cancel (fn []
                           (re-frame/dispatch [::events/set-opened false]))
              :ok-text "Resize"
              :on-ok (fn []
                       (re-frame/dispatch [::events/resize]))}
       [form
        [form-item {:label "Width"
                    :control [input-number {:value (-> settings :target-size :width)
                                            :type "number"
                                            :max project-config/max-sprite-dim
                                            :testid "resize-width"
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-settings-option
                                                                           :target-size
                                                                           (assoc (:target-size settings) :width value)]))}]}]
        [form-item {:label "Height"
                    :control [input-number {:value (-> settings :target-size :height)
                                            :type "number"
                                            :testid "resize-height"
                                            :max project-config/max-sprite-dim
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-settings-option
                                                                           :target-size
                                                                           (assoc (:target-size settings) :height value)]))}]}]
        [form-item {:label "Resize contents"
                    :control [checkbox {:value (:resize-content settings)
                                        :data-testid "checkbox-resize-content"
                                        :on-change (fn [value]
                                                     (re-frame/dispatch [::events/set-settings-option :resize-content value]))}]}]
        [form-item {:label "Anchor"
                    :control [anchor settings]}]
        [previews-container {}
         [previews-grid-items previews]]]])))
