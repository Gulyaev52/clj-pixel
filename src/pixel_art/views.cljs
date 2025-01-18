(ns pixel-art.views
  (:require
   ["antd" :as antd]
   ["react-dnd" :as react-dnd]
   ["react-dnd-html5-backend" :as react-dnd-html5-backend]
   [clojure.string :as string]
   [pixel-art.drawing.events :as drawing.events]
   [pixel-art.drawing.views :refer [drawing drawing-info]]
   [pixel-art.events :as events]
   [pixel-art.export.events :as export]
   [pixel-art.export.views :refer [export-modal]]
   [pixel-art.keyboard-shortcuts-modal.events :as keyboard-shortcuts-modal]
   [pixel-art.keyboard-shortcuts-modal.views :refer [keyboard-shortcuts-modal]]
   [pixel-art.model.color :as color]
   [pixel-art.new-project-modal.events :as new-project-modal]
   [pixel-art.new-project-modal.views :refer [new-project-modal]]
   [pixel-art.palette.views :refer [palettes-section]]
   [pixel-art.project-save-load.events :as project-save-load.events]
   [pixel-art.sprite-resizer.events :as sprite-resizer]
   [pixel-art.sprite-resizer.views :refer [sprite-resizer-modal]]
   [pixel-art.subs :as subs]
   [pixel-art.timeline.views :refer [timeline-panel]]
   [pixel-art.tool.core :as tool]
   [pixel-art.views.color-picker :refer [color-picker]]
   [pixel-art.views.constants :refer [transparent-color-img]]
   [pixel-art.views.ui-kit :refer [button checkbox custom-popover
                                   file-uploader icon-button slider
                                   use-theme-token]]
   [re-frame.core :as re-frame]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(set! *warn-on-infer* false)

(defn- current-color-selection-color-picker [{:keys [value]}]
  (let [initial-value value]
    (fn [{:keys [value on-change]} close]
      [color-picker {:value value
                     :on-change on-change
                     :preset-colors (if (= initial-value color/transparent-color-int)
                                      [{:color initial-value}]
                                      [{:color initial-value}
                                       {:color color/transparent-color-int :title "transparent color"}])
                     :on-cancel close}])))

(defn- current-color-selection [{:keys [value on-change]}]
  [custom-popover
   (fn [close]
     [:div {:style {:width "45px"
                    :height "45px"
                    :border-radius "5px"
                    :cursor "pointer"
                    :background (if (= value color/transparent-color-int)
                                  transparent-color-img
                                  (color/int->rgb-str value))
                    :border "thin solid white"}
            :on-click close}])
   (fn [close]
     [current-color-selection-color-picker {:value value :on-change on-change} close])])

(defn tool-view [{:keys [type selected]}]
  (let [title (string/replace (name type) "-" " ")]
    [:div {:style {:width "50px" :height "50px"}}
     [icon-button {:src type
                   :title title
                   :active selected
                   :size :auto
                   :on-click (fn []
                               (re-frame/dispatch [::events/select-tool type]))}]]))

(defn current-colors-selection []
  (let [primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:style {:width "min-content" :position "relative"}}
     [:div {:style {:width "min-content" :position "relative" :z-index 1}}
      [current-color-selection {:value primary-color
                                :on-change (fn [new-primary-color]
                                             (re-frame/dispatch [::events/set-current-color :primary-color new-primary-color]))}]]
     [:div {:style {:margin-top "-25px" :margin-left "32px"}}
      [current-color-selection {:value secondary-color
                                :on-change (fn [new-secondary-color]
                                             (re-frame/dispatch [::events/set-current-color :secondary-color new-secondary-color]))}]]
     [:div {:style {:position "absolute"
                    :top "48px"
                    :left "3px"
                    :cursor "pointer"}}
      [icon-button {:src :swap
                    :title "Swap colors"
                    :size :sm
                    :on-click (fn [] (re-frame/dispatch [::events/swap-current-colors]))}]]]))

(def-func-component tools-panel []
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
        [tool-view {:type type :selected (= (:type tool) type)}])]

     [:div {:style {:display "flex"
                    :justify-content "center"
                    :margin-top "15px"}}
      [current-colors-selection]]]))

;;----

(defn tool-options-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        options @(re-frame/subscribe [::subs/tool-options]) ;; todo: объединить спеку и опции
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
              on-change #(re-frame/dispatch [::events/change-tool-option (:field option-spec) %])
              props (assoc option-spec
                           :value value
                           :on-change on-change)]
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

(def-func-component header []
  (let [pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])
        theme-token (use-theme-token)]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :padding "5px"
                   :gap "4px"
                   :border-bottom (str "1px solid " (.-colorBorder theme-token))}}
     [:<>
      [new-project-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::new-project-modal/set-opened true]))}
       "New project"]]
     [button {:on-click (fn [] (re-frame/dispatch [::project-save-load.events/save-as-file]))}
      "Save project as file"]
     [file-uploader {:on-upload (fn [file-desc]
                                  (re-frame/dispatch [::project-save-load.events/load-from-file file-desc]))}
      (fn [on-click]
        [button {:on-click on-click}
         "Load project from file"])]
     [:<>
      [button {:on-click (fn [] (re-frame/dispatch [::export/set-opened true]))}
       "Open project export panel"]
      [export-modal]]
     [:<>
      [sprite-resizer-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::sprite-resizer/set-opened true]))} "Resize canvas"]]
     [:<>
      [keyboard-shortcuts-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::keyboard-shortcuts-modal/set-opened true]))} "Keyboard shortcuts"]]
     [checkbox {:value pixels-grid-enabled
                :label "Grid"
                :on-change (fn [checked] (re-frame/dispatch [::drawing.events/enable-pixels-grid checked]))}]

     [:div {:style {:margin-left "auto"}}
      [drawing-info]]]))

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
        [:> antd/Spin {:size "large"}]])
     [:div {:style {:display "flex"
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
       [tools-panel]
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
