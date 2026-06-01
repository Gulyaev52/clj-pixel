(ns pixel-art.new-project-modal.views
  (:require
   [pixel-art.new-project-modal.events :as events]
   [pixel-art.new-project-modal.subs :as subs]
   [pixel-art.project-save-load.events :as project-save-load.events]
   [pixel-art.project-config :as project-config]
   [pixel-art.views.ui-kit :refer [button file-uploader form form-item
                                   input-number input-text modal
                                   replace-current-project-confirm]]
   [re-frame.core :as re-frame]))

(defn new-project-modal []
  (when @(re-frame/subscribe [::subs/opened])
    (let [settings @(re-frame/subscribe [::subs/settings])
          valid @(re-frame/subscribe [::subs/settings-are-valid])]
      [modal {:title "New project"
              :size :md
              :on-cancel (fn []
                           (re-frame/dispatch [::events/set-opened false]))
              :ok-text "Create"
              :ok-data-testid "btn-create-project"
              :on-ok (fn []
                       (when (replace-current-project-confirm)
                         (re-frame/dispatch [::events/create])))
              :ok-disabled (not valid)
              :additional-buttons [[file-uploader {:on-upload (fn [file-desc]
                                                                (when (replace-current-project-confirm)
                                                                  (re-frame/dispatch [::project-save-load.events/load-from-file file-desc])))}
                                    (fn [on-click]
                                      [button {:on-click on-click}
                                       "Open project"])]
                                   [button {:on-click (fn []
                                                        (when (replace-current-project-confirm)
                                                          (re-frame/dispatch [::events/create-example-project])))}
                                    "Create example project"]]}
       [form
        [form-item {:label "Title"
                    :control [input-text {:value (:title settings)
                                          :block true
                                          :testid "input-project-title"
                                          :on-blur (fn [value]
                                                     (re-frame/dispatch [::events/set-title value]))}]}]
        [form-item {:label "Width"
                    :control [input-number {:value (-> settings :size :width)
                                            :block true
                                            :min 1
                                            :max project-config/max-sprite-dim
                                            :testid "input-project-width"
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-width value]))}]}]
        [form-item {:label "Height"
                    :control [input-number {:value (-> settings :size :height)
                                            :block true
                                            :min 1
                                            :max project-config/max-sprite-dim
                                            :testid "input-project-height"
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-height value]))}]}]]])))
