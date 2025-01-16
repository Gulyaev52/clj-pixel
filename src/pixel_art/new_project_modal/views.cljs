(ns pixel-art.new-project-modal.views
  (:require
   [pixel-art.new-project-modal.events :as events]
   [pixel-art.new-project-modal.subs :as subs]
   [pixel-art.project-save-load :as project-save-load]
   [pixel-art.views.ui-kit :refer [button file-uploader form form-item
                                   input-number modal]]
   [re-frame.core :as re-frame]))

(defn new-project-modal []
  (when @(re-frame/subscribe [::subs/opened])
    (let [size @(re-frame/subscribe [::subs/size])]
      [modal {:title "New project"
              :size :md
              :on-cancel (fn []
                           (re-frame/dispatch [::events/set-opened false]))
              :ok-text "Create"
              :on-ok (fn []
                       (re-frame/dispatch [::events/create]))
              :additional-buttons [[file-uploader {:on-upload (fn [file-desc]
                                                                (re-frame/dispatch [::project-save-load/load-from-file file-desc]))}
                                    (fn [on-click]
                                      [button {:on-click on-click}
                                       "Open project"])]
                                   [button {:on-click (fn []
                                                        (re-frame/dispatch [::events/create-example-project]))}
                                    "Create example project"]]}
       [form
        [form-item {:label "Width"
                    :control [input-number {:value (:width size)
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-width value]))}]}]
        [form-item {:label "Height"
                    :control [input-number {:value (:height size)
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::events/set-height value]))}]}]]])))
