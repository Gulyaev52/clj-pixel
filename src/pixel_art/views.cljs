(ns pixel-art.views
  (:require [react :as react]
            [pixel-art.events :as events]
            [pixel-art.subs :as subs]
            [pixel-art.tool.core :as tool]
            [pixel-art.sprite-preview :as sprite-preview]
            [pixel-art.onion-skin :as onion-skin]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]
            [sc.api]))

(def !last-mouse-pos (atom nil))
(def !mouse-down (atom false))

(defn canvas-pos->frame-pos [event scale canvas]
  (let [rect (. canvas getBoundingClientRect)]
    {:x (. js/Math (floor (/ (- (. event -clientX) (. rect -left))
                             scale)))
     :y (. js/Math (floor (/ (- (. event -clientY) (. rect -top))
                             scale)))}))

(defn slider [{:keys [value label min max step onChange]}]
  ;; todo: labels
  [:div {:style {:display :flex :align-items :center}}
   [:span (str label " (" value ")")]
   [:input {:type "range"
            :value value
            :min min
            :max max
            :step step
            :onChange (fn [e]
                        (let [value (parse-double (.. e -target -value))]
                          (onChange value)))}]])

(defn checkbox [{:keys [value onChange label]}]
  [:div {:style {:display :flex :align-items :center}}
   [:input {:type "checkbox"
            :checked value
            :onChange (fn [e] (onChange (.. e -target -checked)))}]
   [:span label]])

(defn options-toolbar [tool-type]
  (let [options @(re-frame/subscribe [::subs/tool-options])
        options-spec (tool/options-specs tool-type)]
    [:div {:style {:display :flex :align-items :center :gap "6px"}}
     (map (fn [option-spec]
            (let [value (get options (:field option-spec))
                  onChange #(re-frame/dispatch [::events/change-tool-option (:field option-spec) %])
                  props (assoc option-spec
                               :value value
                               :onChange onChange)]
              (case (:type option-spec)
                :slider (slider props)
                :checkbox (checkbox props))))
          options-spec)]))

(defn frames []
  (letfn [(box [style onClick children]
            [:div {:onClick onClick
                   :style (merge {:position "absolute"
                                  :display "flex"
                                  :align-items "center"
                                  :justify-content "center"
                                  :width "30px"
                                  :height "30px"
                                  :font-size "14px"
                                  :font-weight "bold"}
                                 style)}
             children])]
    (let [sprite @(re-frame/subscribe [::subs/sprite])
          onion-skin @(re-frame/subscribe [::subs/onion-skin])
          onion-skin-frames-idx (if (:enabled onion-skin)
                                  (onion-skin/get-onion-skin-frames-idx sprite (:frames-count onion-skin))
                                  nil)
          frame-imgs @(re-frame/subscribe [::subs/frame-imgs])
          {:keys [frames current-frame-idx]} sprite]
      [:div {:style {:display :flex :gap "10px"}}
       (for [[idx] (map-indexed vector frames)]
         (let [frame-img (get frame-imgs idx)]
           [:div {:onClick (fn [_] (re-frame/dispatch [::events/select-frame idx]))
                  :style {:position "relative"
                          :width 96
                          :height 96
                          :border-width "3px"
                          :border-style (if (contains? onion-skin-frames-idx idx)
                                          "dashed"
                                          "solid")
                          :border-color (if (= idx current-frame-idx)
                                          "gold" "#444")
                          :border-radius "3px"
                          :imageRendering "pixelated"
                          :backgroundImage (str "url(" frame-img ")")
                          :backgroundSize "contain"}}
            [box {:left 0
                  :top 0
                  :background-color "gold"}
             (fn [_])
             (inc idx)]
            [box {:right 0
                  :top 0
                  :background-color "rgba(100, 100, 100, 0.6)"}
             (fn [_] (re-frame/dispatch [::events/remove-frame idx]))
             "DE"]
            [box {:right 0
                  :bottom 0
                  :background-color "rgba(100, 100, 100, 0.6)"}
             (fn [_] (re-frame/dispatch [::events/duplicate-frame idx]))
             "DU"]
            [box {:left 0
                  :bottom 0
                  :background-color "rgba(100, 100, 100, 0.6)"}
             (fn [_])
             "M"]]))
       [:button {:onClick (fn [_] (re-frame/dispatch [::events/add-frame]))} "new frame"]])))

(defn select [{:keys [value onChange options]}]
  (let [selected-option-idx (ffirst (filter #(= (:value (second %)) value) (map-indexed vector options)))]
    [:select {:value selected-option-idx
              :onChange (fn [event]
                          (let [selected-option (nth options (parse-double (.. event -target -value)) nil)]
                            (when selected-option
                              (onChange (:value selected-option)))))}
     (map-indexed (fn [idx opt] [:option {:value idx} (:label opt)])
                  options)]))

(defn sprite-preview-modal-component []
  (let [{:keys [size displayed-frame-idx]} @(re-frame/subscribe [::subs/sprite-preview])
        frames-size @(re-frame/subscribe [::subs/frames-size])
        frame-imgs @(re-frame/subscribe [::subs/frame-imgs])
        frame-img (or (get frame-imgs displayed-frame-idx nil) (get frame-imgs 0))
        image-size (case size
                     :1x frames-size
                     :2x (update-vals frames-size #(* % 2))
                     :4x (update-vals frames-size #(* % 4))
                     :default {:width 512 :height 512})
        _ (react/useEffect (fn []
                             (.. js/document (addEventListener "keydown" (fn [e]
                                                                           (when (= (.. e -code) "Escape")
                                                                             (re-frame/dispatch [::sprite-preview/close]))))))
                           (array))]
    [:div {:style {:position "fixed"
                   :display "flex"
                   :zIndex 1000
                   :alignItems "center"
                   :justifyContent "center"
                   :left 0
                   :right 0
                   :bottom 0
                   :top 0
                   :backgroundColor "rgba(37,37,37,.9)"}}
     [:div {:style {:backgroundImage (str "url(" frame-img ")")
                    :imageRendering "pixelated"
                    :backgroundSize "contain"
                    :width (:width image-size)
                    :height (:height image-size)}}]]))

(defn sprite-preview-modal []
  [:f> sprite-preview-modal-component])

(defn main-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        scale @(re-frame/subscribe [::subs/scale])
        pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])
        sprite-preview @(re-frame/subscribe [::subs/sprite-preview])
        onion-skin @(re-frame/subscribe [::subs/onion-skin])]
    [:div {:style {:display :flex :flex-direction :column :gap "10px"}}
     [options-toolbar (:type tool)]
     [:div {:style {:display :flex :gap "8px"}}
      (select {:value (:type tool)
               :onChange (fn [tool]
                           (re-frame/dispatch [::events/select-tool tool]))
               :options (map (fn [t] {:value t :label (name t)}) tool/types)})
      [checkbox {:value pixels-grid-enabled
                 :label "grid"
                 :onChange (fn [checked] (re-frame/dispatch [::events/enable-pixels-grid checked]))}]]
     [:div [frames]]
     [:div
      (when (:opened sprite-preview)
        [sprite-preview-modal])
      [:div {:style {:display :flex :gap "4px"}}
       "preview size"
       [select {:value (:size sprite-preview)
                :options (map (fn [s] {:value s :label (name s)}) [:1x :2x :4x :custom])
                :onChange (fn [s] (re-frame/dispatch [::sprite-preview/change-size s]))}]]
      [:div {:style {:display :flex :gap "4px"}}
       "frame speed"
       [select {:value (:frame-speed sprite-preview)
                :options (map (fn [s] {:value s :label (str s " ms")})
                              [25 50 75 100 125 150 200 250 300 350 400 450 500 1000 2500 5000])
                :onChange (fn [s] (re-frame/dispatch [::sprite-preview/change-frame-speed s]))}]]
      [:button {:onClick (fn [] (re-frame/dispatch [::sprite-preview/open]))} "show preview"]]
     [:div
      [:button {:onClick (fn [] (re-frame/dispatch [::onion-skin/set-enabled (not (:enabled onion-skin))]))}
       (if (:enabled onion-skin)
         "disable onion skin"
         "enable onion skin")]
      [:div
       [:div "frames count"]
       [:div {:style {:display :flex :gap "8px"}}
        [:div
         [:span {:style {:margin-right "4px"}} "prev"]
         [:input {:style {:width "50px"}
                  :type "number"
                  :min 0
                  :value (:prev (:frames-count onion-skin))
                  :onChange (fn [e]
                              (re-frame/dispatch [::onion-skin/set-frames-count {:prev (parse-double (.. e -target -value))
                                                                                 :next (:next (:frames-count onion-skin))}]))}]]
        [:div
         [:span {:style {:margin-right "4px"}} "next"]
         [:input {:style {:width "50px"}
                  :type "number"
                  :min 0
                  :value (:next (:frames-count onion-skin))
                  :onChange (fn [e]
                              (re-frame/dispatch [::onion-skin/set-frames-count {:prev (:prev (:frames-count onion-skin))
                                                                                 :next (parse-double (.. e -target -value))}]))}]]]]
      [slider {:min 0 :max 1 :step 0.1
               :value (:opacity onion-skin)
               :label "Opacity"
               :onChange (fn [v] (re-frame/dispatch [::onion-skin/set-opacity v]))}]
      [select {:value (:position onion-skin)
               :options [{:value :front :label "in front of sprite"}
                         {:value :behind :label "behind sprite"}]
               :onChange (fn [v] (re-frame/dispatch [::onion-skin/set-position v]))}]]
     [:div {:style {:display :flex :justify-content :center}}
      [:div {:style {:position "relative"
                     :border "1px solid black"}
             :onMouseDown (fn [event]
                            (let [elem (. js/document (getElementById "tutorial"))

                                  mouse-pos (canvas-pos->frame-pos event scale elem)

                                  mouse-move (fn [event]
                                               (let [mouse-pos (canvas-pos->frame-pos event scale elem)]
                                                 (when (not= mouse-pos @!last-mouse-pos)
                                                   (reset! !last-mouse-pos mouse-pos)
                                                   (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos]))))

                                  mouse-up (fn mouse-up [event]
                                             (let [mouse-pos (canvas-pos->frame-pos event scale elem)]
                                               (reset! !last-mouse-pos mouse-pos)
                                               (reset! !mouse-down false)
                                               (re-frame/dispatch [::events/handle-mouse-event :mouse-up mouse-pos])
                                               (.. js/document (removeEventListener "mousemove" mouse-move))
                                               (.. js/document (removeEventListener "mouseup" mouse-up))))]
                              (re-frame/dispatch [::events/handle-mouse-event :mouse-down mouse-pos])
                              (reset! !mouse-down true)
                              (.. js/document (addEventListener "mousemove" mouse-move))
                              (.. js/document (addEventListener "mouseup" mouse-up))))
             :onMouseLeave (fn [event]
                             (when-not @!mouse-down
                               (let [mouse-pos (canvas-pos->frame-pos event scale (. js/document (getElementById "tutorial")))]
                                 (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos]))))
             :onMouseMove (fn [event]
                            (when-not @!mouse-down
                              (let [mouse-pos (canvas-pos->frame-pos event scale (. js/document (getElementById "tutorial")))]
                                (when (not= mouse-pos @!last-mouse-pos)
                                  (reset! !last-mouse-pos mouse-pos)
                                  (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos])))))}
       [:canvas {:id "tutorial"
                 :style {:position "relative"
                         :zIndex 1}}]
       [:canvas {:id "onion-skin"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :zIndex (if (= (:position onion-skin) :front)
                                   2 0)}}]
       [:canvas {:id "preview"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :zIndex 3}}]
       [:canvas {:id "grid"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :zIndex 4}}]]]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
