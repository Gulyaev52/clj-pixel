(ns pixel-art.palette.views
  (:require
   [pixel-art.model.color :as color]
   [pixel-art.palette.events :as events]
   [pixel-art.palette.gimp-file :as gimp-file]
   [pixel-art.subs :as common-subs]
   [pixel-art.utils.coll :as coll]
   [pixel-art.views.color-picker :refer [color-picker]]
   [pixel-art.views.ui-kit :refer [button custom-popover file-uploader
                                   icon-button select use-theme-token]]
   [re-frame.core :as re-frame]
   [reagent.core :as reag]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

;; todo: зачем-это?
(defn- replace-transparent-color [color]
  (if (= color color/transparent-color-int)
    (color/int 0 0 0)
    color))

(defn add-new-color-picker []
  (let [primary-color (replace-transparent-color @(re-frame/subscribe [::common-subs/primary-color]))
        secondary-color (replace-transparent-color @(re-frame/subscribe [::common-subs/secondary-color]))
        last-color (replace-transparent-color (last (:colors @(re-frame/subscribe [::common-subs/current-palette]))))
        !temp-new-value (reag/atom primary-color)]
    (fn [{:keys [on-close]}]
      [color-picker {:value @!temp-new-value
                     :on-change (fn [new-value]
                                  (reset! !temp-new-value new-value))
                     :actions [[button
                                {:on-click (fn []
                                             (re-frame/dispatch [::events/add-color @!temp-new-value])
                                             (on-close))
                                 :data-testid "add-color-confirm-button"}
                                "Add"]]
                     :preset-colors (coll/distinct-by :color [{:color primary-color}
                                                              {:color secondary-color}
                                                              {:color last-color}])
                     :on-cancel (fn []
                                  (on-close))}])))

(def-func-component palette-colors [{:keys [colors primary-color secondary-color]}]
  (let [theme-token (use-theme-token)]
    [:div {:style {:display :grid
                   :grid-template-columns "repeat(auto-fill, 35px)" ;; todo: dynamic
                   :grid-auto-rows "35px"
                   :grid-gap "2px"
                   :height "100%"
                   :overflow "auto"}}
     (doall
      (for [[idx color] (map-indexed vector colors)]
        (let [color-dark? (.. (color/->tinycolor color) isDark)
              color-str (color/int->rgb-str color)]
          ^{:key color}
          [:div {:className "color-container"
                 :data-testid (str "palette-color-" color-str)
                 :style {:background-color color-str
                         :position "relative"
                         :cursor "pointer"
                         :color (if color-dark? (.-colorText theme-token) (.-colorBgBase theme-token))}
                 :on-click (fn []
                             (re-frame/dispatch [::events/select-color idx false]))
                 :on-context-menu (fn [e]
                                    (. e preventDefault)
                                    (re-frame/dispatch [::events/select-color idx true]))}
           (when (= color primary-color) "L")
           (when (= color secondary-color) "R")
           [:div {:className "remove-color-button"
                  :style {:position "absolute"
                          :right "1px"
                          :top "1px"
                          :opacity 0}}
            [icon-button {:src :close
                          :icon-theme (if color-dark? :light :dark)
                          :title "remove color"
                          :data-testid (str "remove-palette-color-" idx)
                          :size :xs
                          :on-click (fn [e]
                                      (.. e (stopPropagation))
                                      (re-frame/dispatch [::events/remove-color idx]))}]]])))]))

(defn palettes-section []
  (let [palettes @(re-frame/subscribe [::common-subs/palettes])
        current-palette-idx (coll/find-first-idx :current palettes)
        current-palette (coll/find-first :current palettes)
        primary-color @(re-frame/subscribe [::common-subs/primary-color])
        secondary-color @(re-frame/subscribe [::common-subs/secondary-color])
        can-delete-palette? (> (count palettes) 1)]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :height "300px"}}
     [select {:value current-palette-idx
              :options (map-indexed (fn [idx p] {:value idx :label (:name p)}) palettes)
              :block true
              :size :sm
              :data-testid "palette-select"
              :on-change (fn [idx]
                           (re-frame/dispatch [::events/select-palette idx]))}]

     [:div {:style {:display "flex" :justify-content "space-between"}}
      [custom-popover
       (fn [close]
         [icon-button {:src :add
                       :title "Add color"
                       :data-testid "btn-add-color"
                       :size :sm
                       :on-click close}])
       (fn [close]
         [add-new-color-picker {:on-close close}])]

      [:div
       [icon-button {:src :new-palette
                     :title "Add palette"
                     :data-testid "btn-add-palette"
                     :size :sm
                     :on-click (fn []
                                 (when-let [name (js/prompt "palette name")]
                                   (re-frame/dispatch [::events/create-palette name])))}]
       [icon-button {:src :remove
                     :title "Remove palette"
                     :data-testid "remove-palette-button"
                     :size :sm
                     :disabled (not can-delete-palette?)
                     :on-click (fn []
                                 (when (js/confirm "Are you sure?")
                                   (re-frame/dispatch [::events/remove-selected-palette])))}]
       [icon-button {:src :edit
                     :title "Rename palette"
                     :data-testid "rename-palette-button"
                     :size :sm
                     :on-click (fn []
                                 (when-let [name (js/prompt "New palette name" (:name current-palette))]
                                   (re-frame/dispatch [::events/rename-selected-palette name])))}]
       [icon-button {:src :adjust
                     :title "Add colors from current frame"
                     :data-testid "btn-add-colors-from-frame"
                     :size :sm
                     :on-click (fn []
                                 (re-frame/dispatch [::events/add-colors-from-frame]))}]]

      [:div
       [icon-button {:src :file-export
                     :title "Export palette"
                     :data-testid "export-palette-button"
                     :size :sm
                     :on-click (fn [] (re-frame/dispatch [::events/export-palette]))}]
       [file-uploader {:accept (str "." gimp-file/ext)
                       :data-testid "import-palette-input"
                       :on-upload (fn [file-desc]
                                    (re-frame/dispatch [::events/import-palette file-desc]))}
        (fn [on-click]
          [icon-button {:src :file-import
                        :title "Import palette"
                        :size :sm
                        :on-click on-click}])]]]

     [:div {:style {:flex-grow 1 :min-height 0}}
      [palette-colors {:colors (:colors current-palette)
                       :primary-color primary-color
                       :secondary-color secondary-color}]]]))
