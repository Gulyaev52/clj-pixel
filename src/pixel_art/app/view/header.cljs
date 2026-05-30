(ns pixel-art.app.view.header
  (:require
   [clojure.string :as string]
   [pixel-art.drawing.events :as drawing.events]
   [pixel-art.drawing.views :refer [drawing-info]]
   [pixel-art.app.events :as events]
   [pixel-art.export-modal.events :as export-modal.events]
   [pixel-art.export-modal.views :refer [export-modal]]
   [pixel-art.history.events :as history.events]
   [pixel-art.history.subs :as history.subs]
   [pixel-art.keyboard-shortcuts-modal.events :as keyboard-shortcuts-modal.events]
   [pixel-art.keyboard-shortcuts-modal.views :refer [keyboard-shortcuts-modal]]
   [pixel-art.new-project-modal.events :as new-project-modal.events]
   [pixel-art.new-project-modal.views :refer [new-project-modal]]
   [pixel-art.project-save-load.events :as project-save-load.events]
   [pixel-art.sprite-resizer.events :as sprite-resizer.events]
   [pixel-art.sprite-resizer.views :refer [sprite-resizer-modal]]
   [pixel-art.app.subs :as subs]
   [pixel-art.views.ui-kit :refer [button checkbox file-uploader icon-button
                                   replace-current-project-confirm
                                   space title use-theme-token]]
   [re-frame.core :as re-frame]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(defn- undo-redo-buttons []
  (let [undo-available? @(re-frame/subscribe [::history.subs/undo-available?])
        redo-available? @(re-frame/subscribe [::history.subs/redo-available?])]
    [:<>
     [button {:on-click (fn [] (re-frame/dispatch [::history.events/undo]))
              :disabled (not undo-available?)
              :data-testid "btn-undo"}
      "Undo"]
     [button {:on-click (fn [] (re-frame/dispatch [::history.events/redo]))
              :disabled (not redo-available?)
              :data-testid "btn-redo"}
      "Redo"]]))

(defn- project-title []
  (let [unsaved-changes-exist @(re-frame/subscribe [::subs/unsaved-changes-exist])
        sprite-title @(re-frame/subscribe [::subs/sprite-title])]
    [space {:style {:min-width 0} :styles {:item {:min-width 0}}}
     [title {:level 3 :style {:margin 0
                              :white-space "nowrap"
                              :overflow "hidden"
                              :text-overflow "ellipsis"}}
      (str sprite-title (when unsaved-changes-exist "*"))]
     [icon-button {:src :pen
                   :title "edit title"
                   :data-testid "btn-edit-title"
                   :size :xs
                   :on-click (fn []
                               (let [new-title (js/prompt "Title")]
                                 (when-not (string/blank? new-title)
                                   (re-frame/dispatch [::events/set-sprite-title new-title]))))}]]))

(defn- pixels-grid-checkbox []
  (let [pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])]
    [checkbox {:value pixels-grid-enabled
               :label "Grid"
               :on-change (fn [checked] (re-frame/dispatch [::drawing.events/enable-pixels-grid checked]))}]))

(def-func-component header []
  (let [theme-token (use-theme-token)]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :padding "5px"
                   :gap "4px"
                   :border-bottom (str "1px solid " (.-colorBorder theme-token))}}
     [:<>
      [new-project-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::new-project-modal.events/set-opened true]))
               :data-testid "btn-new-project"}
       "New project"]]
     [button {:on-click (fn [] (re-frame/dispatch [::project-save-load.events/save-as-file]))
              :data-testid "btn-save-as-file"}
      "Save as file"]
     [button {:on-click (fn [] (re-frame/dispatch [::project-save-load.events/save-in-browser]))
              :data-testid "btn-save-in-browser"}
      "Save in browser"]
     [file-uploader {:accept (str "." project-save-load.events/file-ext)
                     :data-testid "input-load-from-file"
                     :on-upload (fn [file-desc]
                                  (when (replace-current-project-confirm)
                                    (re-frame/dispatch [::project-save-load.events/load-from-file file-desc])))}
      (fn [on-click]
        [button {:on-click on-click}
         "Load from file"])]
     [:<>
      [button {:on-click (fn [] (re-frame/dispatch [::export-modal.events/set-opened true]))
               :data-testid "btn-export"}
       "Export"]
      [export-modal]]
     [:<>
      [sprite-resizer-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::sprite-resizer.events/set-opened true]))
               :data-testid "btn-resize-canvas"}
       "Resize canvas"]]
     [:<>
      [keyboard-shortcuts-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::keyboard-shortcuts-modal.events/set-opened true]))
               :data-testid "btn-keyboard-shortcuts"}
       "Keyboard shortcuts"]]
     [pixels-grid-checkbox]
     [undo-redo-buttons]
     [project-title]
     [:div {:style {:margin-left "auto" :flex-shrink 0}}
      [drawing-info]]]))
