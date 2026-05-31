(ns pixel-art.timeline.view.toolbar
  (:require
   ["antd" :as antd]
   [clojure.string :as string]
   [pixel-art.onion-skin.events :as onion-skin.events]
   [pixel-art.onion-skin.subs :as onion-skin.subs]
   [pixel-art.onion-skin.views :refer [onion-skin-settings]]
   [pixel-art.sprite-preview.events :as sprite-preview.events]
   [pixel-art.sprite-preview.views :refer [sprite-preview-modal]]
   [pixel-art.timeline.events :as events]
   [pixel-art.views.ui-kit :refer [button hint icon-button input-number
                                   popover space typography]]
   [re-frame.core :as re-frame]
   [sc.api]))

(defn- section [title children]
  [:> antd/Card {:size "small"}
   [space
    [typography title]
    (into [:div {:style {:display "flex" :align-items "center"}}]
          children)]])

(defn toolbar [{:keys [disabled-actions current-frame]}]
  (let [onion-skin-enabled @(re-frame/subscribe [::onion-skin.subs/enabled])]
    [:div {:style {:display "flex" :justify-content "space-between"}}
     [space
      [section "Frames" [[icon-button {:src :add
                                       :title "add empty frame"
                                       :data-testid "btn-add-empty-frame"
                                       :disabled (:add-frame disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/add-frame]))}]
                         [icon-button {:src :remove
                                       :title "remove frame"
                                       :data-testid "btn-remove-frame"
                                       :disabled (:remove-frame disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/remove-frame]))}]
                         [icon-button {:src :duplicate
                                       :title "duplicate frame"
                                       :data-testid "btn-duplicate-frame"
                                       :disabled (:duplicate-frame disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/duplicate-frame]))}]
                         [icon-button {:src :arrow-left
                                       :title "move frame left"
                                       :data-testid "btn-move-frame-left"
                                       :disabled (:move-frame-left disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-frame-left]))}]
                         [icon-button {:src :arrow-right
                                       :title "move frame right"
                                       :data-testid "btn-move-frame-right"
                                       :disabled (:move-frame-right disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-frame-right]))}]
                         [hint "A frame is one snapshot in time of your animation. Drag a frame by its number to reorder it."]]]

      [section "Layers" [[icon-button {:src :add
                                       :title "add layer"
                                       :data-testid "btn-add-layer"
                                       :disabled (:add-layer disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/add-layer]))}]
                         [icon-button {:src :remove
                                       :title "remove layer"
                                       :data-testid "btn-remove-layer"
                                       :disabled (:remove-layer disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/remove-layer]))}]
                         [icon-button {:src :duplicate
                                       :title "duplicate layer"
                                       :data-testid "btn-duplicate-layer"
                                       :disabled (:duplicate-layer disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/duplicate-layer]))}]
                         [icon-button {:src :merge-down
                                       :title "merge layer with below"
                                       :data-testid "btn-merge-layer-with-below"
                                       :disabled (:merge-layer-with-below disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/merge-layer-with-below]))}]
                         [icon-button {:src :arrow-up
                                       :title "move layer up"
                                       :data-testid "btn-move-layer-up"
                                       :disabled (:move-layer-up disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-layer-up]))}]
                         [icon-button {:src :arrow-down
                                       :title "move layer down"
                                       :data-testid "btn-move-layer-down"
                                       :disabled (:move-layer-down disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-layer-down]))}]
                         [icon-button {:src :edit
                                       :title "rename layer"
                                       :data-testid "btn-rename-layer"
                                       :size :sm
                                       :on-click (fn [e]
                                                   (. e stopPropagation)
                                                   (when-let [new-name (js/prompt "New layer name" (:name current-frame))]
                                                     (when (seq (string/trim new-name))
                                                       (re-frame/dispatch [::events/rename-layer new-name]))))}]
                         [hint "A layer is a transparent sheet you draw on. Layers stack on top of each other to form the final image. Drag a layer by its name to reorder it."]]]

      [section "Cels" [[icon-button {:src :link
                                     :title "link cels"
                                     :data-testid "btn-link-cels"
                                     :disabled (:link-cels disabled-actions)
                                     :size :sm
                                     :on-click (fn [] (re-frame/dispatch [::events/link-selected-cels]))}]
                       [icon-button {:src :link-off
                                     :title "unlink cels"
                                     :data-testid "btn-unlink-cels"
                                     :disabled (:unlink-cel disabled-actions)
                                     :size :sm
                                     :on-click (fn [] (re-frame/dispatch [::events/unlink-selected-cels]))}]
                       [hint "A cel (from celluloid) is one image in a specific frame and layer, at a specific xy-coordinate in the canvas. Cels can be linked. Linked cels share the same pixels — editing one updates all cels linked to it across frames. To select: Ctrl+Click cels on the same layer, then press 'Link Cels'."]]]

      [section "Onion skin"
       [[icon-button {:src (if onion-skin-enabled :layers-off :layers)
                      :title (if onion-skin-enabled "disable onion skin" "enable onion skin")
                      :data-testid "btn-toggle-onion-skin"
                      :size :sm
                      :on-click (fn [] (re-frame/dispatch [::onion-skin.events/set-enabled (not onion-skin-enabled)]))}]
        [popover
         (fn [open]
           [icon-button {:src :cog
                         :title "onion skin settings"
                         :data-testid "btn-onion-skin-settings"
                         :size :sm
                         :on-click open}])
         [onion-skin-settings]]]]]

     [:div {:style {:display :flex}}
      [:div {:style {:display "flex" :flex-direction "column" :gap "4px"}}
       [typography "Frame duration (ms)"]
       [input-number {:value (:duration current-frame)
                      :testid "input-frame-duration"
                      :on-blur (fn [duration]
                                 (re-frame/dispatch [::events/set-frame-duration (:idx current-frame) duration]))}]]
      [:<>
       [sprite-preview-modal]
       [:div {:style {:margin-top "auto"}}
        [button {:on-click (fn [] (re-frame/dispatch [::sprite-preview.events/open]))} "Show preview"]]]]]))
