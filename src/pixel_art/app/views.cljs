(ns pixel-art.app.views
  (:require
   ["antd" :as antd]
   ["react-dnd" :as react-dnd]
   ["react-dnd-html5-backend" :as react-dnd-html5-backend]
   [clojure.string :as string]
   [pixel-art.app.events :as events]
   [pixel-art.app.subs :as subs]
   [pixel-art.app.view.current-colors-selection :refer [current-colors-selection]]
   [pixel-art.app.view.header :refer [header]]
   [pixel-art.drawing.views :refer [drawing]]
   [pixel-art.palette.views :refer [palettes-section]]
   [pixel-art.timeline.views :refer [timeline-panel]]
   [pixel-art.tool.core :as tool]
   [pixel-art.views.ui-kit :refer [checkbox icon-button slider use-theme-token]]
   [re-frame.core :as re-frame]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(set! *warn-on-infer* false)

(defn- tool-button [{:keys [type selected]}]
  (let [title (string/replace (name type) "-" " ")]
    [:div {:style {:width "50px" :height "50px"}
           :data-testid (str "tool-" (name type))}
     [icon-button {:src type
                   :title title
                   :active selected
                   :size :auto
                   :on-click (fn []
                               (re-frame/dispatch [::events/select-tool type]))}]]))

(def-func-component left-sidebar []
  (let [tool @(re-frame/subscribe [::subs/tool])
        theme-token (use-theme-token)]
    [:div {:style {:width "100px"
                   :border-right (str "1px solid " (.-colorBorder theme-token))}}
     [:div {:style {:display "grid"
                    :grid-template-columns "1fr 1fr"
                    :gap "1px"
                    :padding "1px"}}
      (for [type tool/types]
        ^{:key (name type)}
        [tool-button {:type type :selected (= (:type tool) type)}])]

     [:div {:style {:display "flex"
                    :justify-content "center"
                    :margin-top "15px"}}
      [current-colors-selection]]]))

(defn tool-options-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        options @(re-frame/subscribe [::subs/tool-options])
        options-spec (tool/options-specs (:type tool))]
    [:div {:style {:display :flex
                   :align-items :center
                   :height "30px"
                   :flex-shrink 0
                   :padding "0 10px"
                   :gap "12px"}}
     (doall
      (for [[idx option-spec] (map-indexed vector options-spec)]
        (let [value (get options (:field option-spec))
              on-change #(re-frame/dispatch [::events/set-tool-option (:field option-spec) %])
              props (assoc option-spec
                           :value value
                           :on-change on-change
                           :data-testid (:field option-spec))]
          ^{:key idx}
          [:div
           (case (:type option-spec)
             :slider [:div {:style {:width "300px"}}
                      (slider props)]
             :checkbox (checkbox props))])))]))

(def-func-component right-sidebar []
  (let [theme-token (use-theme-token)]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :height "100%"
                   :padding "1px"
                   :border-left (str "1px solid " (.-colorBorder theme-token))}}
     [:div {:style {:margin-top "auto"}}
      [palettes-section]]]))

(def-func-component app-content []
  (let [initial-loading @(re-frame/subscribe [::subs/initial-loading])
        colorBgContainer (.. antd/theme useToken -token -colorBgContainer)]
    [:> react-dnd/DndProvider {"backend" react-dnd-html5-backend/HTML5Backend}
     (when initial-loading
       [:div {:style {:position "fixed"
                      :z-index 1000
                      :display "flex"
                      :align-items "center"
                      :justify-content "center"
                      :width "100%"
                      :height "100%"
                      :background-color "rgba(0, 0, 0, 0.8)"}}
        [:> antd/Spin {:size "large" :data-testid "app-spinner"}]])
     [:div {:data-testid (when-not initial-loading "ready")
            :style {:display "flex"
                    :flex-direction "column"
                    :height "100%"
                    :width "100%"
                    :max-height "100%"
                    :max-width "100%"
                    :background-color colorBgContainer}}
      [header]
      [:div {:style {:display :grid
                     :grid-template-columns "100px 1fr 250px"
                     :flex-grow 1
                     :min-height 0
                     :width "100%"}}
       [left-sidebar]
       [:div {:style {:display :flex
                      :flex-direction :column
                      :min-width 0
                      :min-height 0}}
        [tool-options-panel]
        [drawing]
        [timeline-panel]]
       [right-sidebar]]]]))

(defn app []
  [:> antd/ConfigProvider {"theme" {"token" {"motion" false}
                                    "algorithm" (. antd/theme -darkAlgorithm)}}
   [app-content]])
