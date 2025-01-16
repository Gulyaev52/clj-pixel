(ns pixel-art.export.views
  (:require
   [pixel-art.export.events :as events]
   [pixel-art.subs :as common-subs]
   [pixel-art.export.subs :as subs]
   [pixel-art.views.preview :refer [preview-image previews-container
                                    previews-grid-items]]
   [pixel-art.views.ui-kit :refer [checkbox form
                                   form-item input-number
                                   input-text modal radio-group select
                                   slider space]]
   [re-frame.core :as re-frame]
   [sc.api]))

(defn export-common-settings-fields [{:keys [common-settings type-options]}]
  (let [layers @(re-frame/subscribe [::common-subs/layers])
        layer-options (concat
                       [{:label "Visible layers" :value {:type :visible}}
                        {:label "Selected layers" :value {:type :selected}}]
                       (map-indexed (fn [idx l] {:label (:name l) :value {:type :layer :idx idx}}) layers))]
    [[form-item {:label "Frames"
                 :control [select {:value (:frames common-settings)
                                   :options [{:label "All frames" :value :all}
                                             {:label "Selected frames" :value :selected}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :frames value]))}]}]
     [form-item {:label "Layers"
                 :control [select {:value (:layers common-settings)
                                   :options layer-options
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :layers value]))}]}]
     [form-item {:label "Direction"
                 :control [select {:value (:direction common-settings)
                                   :options [{:label "Forward" :value :forward}
                                             {:label "Backwards" :value :backwards}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :direction value]))}]}]
     [form-item {:label "Frame scale"
                 :control [slider {:value (:scale common-settings)
                                   :min events/min-scale
                                   :max events/max-scale
                                   :block true
                                   :step 1
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :scale value]))}]}]
     [form-item {:label "Frame size"
                 :control [:<> (str (-> common-settings :scaled-frame-size :width)
                                    "x"
                                    (-> common-settings :scaled-frame-size :height))
                           [:br]]}]
     [form-item {:label "File"
                 :control [input-text {:value (:file-name common-settings)
                                       :on-blur (fn [value]
                                                  (re-frame/dispatch [::events/set-settings-option :file-name value]))}]}]
     [form-item {:label "Type"
                 :control [select {:value (:file-type common-settings)
                                   :options type-options
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::events/set-settings-option :file-type value]))}]}]
     [form-item {:label "Split layers"
                 :control [checkbox {:value (:split-layers common-settings)
                                     :on-change (fn [value]
                                                  (re-frame/dispatch [::events/set-settings-option :split-layers value]))}]}]]))

(defn export-image-settings-form []
  (let [image-settings @(re-frame/subscribe [::subs/image-settings])]
    (into [form]
          (into
           (export-common-settings-fields
            {:common-settings image-settings
             :type-options [{:label "png" :value :png}
                            {:label "gif" :value :gif}]})
           (when (= (:file-type image-settings) :gif)
             [[form-item
               "Never repeat" [checkbox {:value (not (:repeat image-settings))
                                         :on-change (fn [value]
                                                      (re-frame/dispatch [::events/set-settings-option :repeat (not value)]))}]]])))))

(defn export-spritesheet-settings-form []
  (let [settings @(re-frame/subscribe [::subs/spritesheet-settings])]
    (into [form]
          (into
           [[form-item {:label "Columns"
                        :control [input-number {:value (:columns settings)
                                                :block true
                                                :on-blur (fn [value]
                                                           (re-frame/dispatch [::events/set-settings-option :columns value]))}]}]
            [form-item {:label "Rows"
                        :control [:div (:rows settings)]}]]
           (export-common-settings-fields
            {:common-settings settings
             :type-options [{:label "png" :value :png}]})))))

(defn export-modal []
  (when @(re-frame/subscribe [::subs/modal-opened])
    (let [current-tab @(re-frame/subscribe [::subs/current-tab])
          exporting @(re-frame/subscribe [::subs/exporting])
          export-settings-valid? @(re-frame/subscribe [::subs/settings-valid?])
          spritesheet-settings @(re-frame/subscribe [::subs/spritesheet-settings])
          preview @(re-frame/subscribe [::subs/preview])]
      [modal {:title "Export"
              :size :lg
              :on-cancel (fn []
                           (re-frame/dispatch [::events/set-opened false]))
              :ok-text "Export"
              :ok-disabled (or exporting (not export-settings-valid?))
              :on-ok (fn []
                       (re-frame/dispatch [::events/export]))}

       [space {:direction "vertical" :block true}
        [radio-group {:value current-tab
                      :options [{:value :image :label "Image"}
                                {:value :spritesheet :label "Spritesheet"}]
                      :on-change (fn [tab]
                                   (re-frame/dispatch [::events/select-tab tab]))}]

        [previews-container {:loading (:generation preview)}
         (case current-tab
           :spritesheet [preview-image (:data preview) {:height (* (:rows spritesheet-settings) 100)
                                                        :margin "auto"}]
           :image [previews-grid-items (:data preview)])]

        [:div {:style {:display :grid}}
         (case current-tab
           :image [export-image-settings-form]
           :spritesheet [export-spritesheet-settings-form])]]])))
