(ns pixel-art.app.view.current-colors-selection
  (:require
   [pixel-art.app.events :as events]
   [pixel-art.model.color :as color]
   [pixel-art.app.subs :as subs]
   [pixel-art.views.color-picker :refer [color-picker]]
   [pixel-art.views.constants :refer [transparent-color-img]]
   [pixel-art.views.ui-kit :refer [custom-popover icon-button]]
   [re-frame.core :as re-frame]
   [sc.api]))

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

(defn- current-color-selection [{:keys [value on-change data-testid]}]
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
            :data-testid data-testid
            :on-click close}])
   (fn [close]
     [current-color-selection-color-picker {:value value :on-change on-change} close])])

(defn current-colors-selection []
  (let [primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:style {:width "min-content" :position "relative"}}
     [:div {:style {:width "min-content" :position "relative" :z-index 1}}
      [current-color-selection {:value primary-color
                                :data-testid "primary-color-swatch"
                                :on-change (fn [new-primary-color]
                                             (re-frame/dispatch [::events/set-current-color :primary-color new-primary-color]))}]]
     [:div {:style {:margin-top "-25px" :margin-left "32px"}}
      [current-color-selection {:value secondary-color
                                :data-testid "secondary-color-swatch"
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
