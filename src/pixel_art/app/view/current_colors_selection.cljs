(ns pixel-art.app.view.current-colors-selection
  (:require
   [pixel-art.app.events :as events]
   [pixel-art.model.color :as color]
   [pixel-art.app.subs :as subs]
   [pixel-art.views.color-picker :refer [color-picker]]
   [pixel-art.views.ui-kit :refer [custom-popover icon-button]]
   [re-frame.core :as re-frame]
   [sc.api]
   [shadow.css :refer (css)]))

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
     [:div {:class (css {:width "45px"
                         :height "45px"
                         :border-radius "5px"
                         :cursor "pointer"
                         :border "thin solid white"})
            :style {:background (if (= value color/transparent-color-int)
                                  "var(--pixel-transparent-img)"
                                  (color/int->rgb-str value))}
            :data-testid data-testid
            :on-click close}])
   (fn [close]
     [current-color-selection-color-picker {:value value :on-change on-change} close])])

(defn current-colors-selection []
  (let [primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:class (css {:width "min-content" :position "relative"})}
     [:div {:class (css {:width "min-content" :position "relative" :z-index 1})}
      [current-color-selection {:value primary-color
                                :data-testid "primary-color-swatch"
                                :on-change (fn [new-primary-color]
                                             (re-frame/dispatch [::events/set-current-color :primary-color new-primary-color]))}]]
     [:div {:class (css {:margin-top "-25px" :margin-left "32px"})}
      [current-color-selection {:value secondary-color
                                :data-testid "secondary-color-swatch"
                                :on-change (fn [new-secondary-color]
                                             (re-frame/dispatch [::events/set-current-color :secondary-color new-secondary-color]))}]]
     [:div {:class (css {:position "absolute"
                         :top "48px"
                         :left "3px"
                         :cursor "pointer"})}
      [icon-button {:src :swap
                    :title "Swap colors"
                    :size :sm
                    :on-click (fn [] (re-frame/dispatch [::events/swap-current-colors]))}]]]))
