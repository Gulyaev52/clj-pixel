(ns pixel-art.export-modal.views
  (:require
   [pixel-art.export-modal.events :as events]
   [pixel-art.app.subs :as common-subs]
   [pixel-art.export-modal.subs :as subs]
   [pixel-art.views.preview :refer [previews-container
                                    previews-grid-items]]
   [pixel-art.views.ui-kit :refer [checkbox form
                                   form-item
                                   input-text modal select
                                   slider space]]
   [re-frame.core :as re-frame]
   [sc.api]
   [shadow.css :refer (css)]))

(defn- settings-form []
  (let [settings @(re-frame/subscribe [::subs/settings])
        layers @(re-frame/subscribe [::common-subs/layers])
        layer-options (concat
                       [{:label "Visible layers" :value {:type :visible}}
                        {:label "Selected layers" :value {:type :selected}}]
                       (map-indexed (fn [idx l] {:label (:name l) :value {:type :layer :idx idx}}) layers))]
    [form
     [form-item {:label "Frames"
                 :control [select {:value (:frames settings)
                                   :data-testid "export-frames"
                                   :options [{:label "All frames" :value :all}
                                             {:label "Selected frames" :value :selected}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :frames value]))}]}]
     [form-item {:label "Layers"
                 :control [select {:value (:layers settings)
                                   :data-testid "export-layers"
                                   :options layer-options
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :layers value]))}]}]
     [form-item {:label "Direction"
                 :control [select {:value (:direction settings)
                                   :data-testid "export-direction"
                                   :options [{:label "Forward" :value :forward}
                                             {:label "Backwards" :value :backwards}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :direction value]))}]}]
     [form-item {:label "Frame scale"
                 :control [slider {:value (:scale settings)
                                   :min events/min-scale
                                   :max events/max-scale
                                   :block true
                                   :step 1
                                   :data-testid "export-scale-slider"
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :scale value]))}]}]
     [form-item {:label "Frame size"
                 :control [:div {:data-testid "export-frame-size"}
                           (str (-> settings :scaled-frame-size :width)
                                "x"
                                (-> settings :scaled-frame-size :height))]}]
     [form-item {:label "File"
                 :control [input-text {:value (:file-name settings)
                                       :testid "export-file-name"
                                       :on-blur (fn [value]
                                                  (re-frame/dispatch [::events/set-settings-option :file-name value]))}]}]
     [form-item {:label "Type"
                 :control [select {:value (:file-type settings)
                                   :data-testid "export-type"
                                   :options [{:label "png" :value :png}
                                             {:label "gif" :value :gif}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :file-type value]))}]}]
     (when (= (:file-type settings) :gif)
       [form-item {:label "Never repeat"
                   :control [checkbox {:value (not (:repeat settings))
                                       :on-change (fn [value]
                                                    (re-frame/dispatch [::events/set-settings-option :repeat (not value)]))}]}])
     [form-item {:label "Split layers"
                 :control [checkbox {:value (:split-layers settings)
                                     :data-testid "export-split-layers"
                                     :on-change (fn [value]
                                                  (re-frame/dispatch [::events/set-settings-option :split-layers value]))}]}]]))

(defn export-modal []
  (when @(re-frame/subscribe [::subs/modal-opened])
    (let [exporting @(re-frame/subscribe [::subs/exporting])
          export-settings-valid? @(re-frame/subscribe [::subs/settings-valid?]) 
          preview @(re-frame/subscribe [::subs/preview])]
      [modal {:title "Export"
              :size :lg
              :ok-data-testid "btn-export-ok"
              :on-cancel (fn []
                           (re-frame/dispatch [::events/set-opened false]))
              :ok-text "Export"
              :ok-disabled (or exporting (not export-settings-valid?))
              :on-ok (fn []
                       (re-frame/dispatch [::events/export]))}

       [space {:direction "vertical" :block true} 
        [previews-container {:loading (:generation preview)}
         [previews-grid-items (:data preview)]]
        [:div {:class (css {:display "grid"})}
         [settings-form]]]])))
