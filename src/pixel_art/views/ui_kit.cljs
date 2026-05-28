(ns pixel-art.views.ui-kit
  (:require
   ["antd" :as antd]
   [reagent.core :as reag]
   [react :as react]
   [sc.api]
   [clojure.core :as c])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(defn use-theme-token []
  (.. antd/theme useToken -token))

(defn typography
  ([text] (typography {} text))
  ([props text] [:> antd/Typography props text]))

(defn space [& children]
  (let [props (first children)
        items (if (map? props)
                (rest children)
                children)]
    (into [:> antd/Space (if (map? props)
                           (merge (dissoc props :block)
                                  (when (:block props)
                                    {:style {:width "100%"}}))
                           {})]
          items)))

(defn title
  ([text] (title text {}))
  ([props title] [:> (. antd/Typography -Title)
                  (assoc-in props [:style :margin-top] 0) ;; todo: fix
                  title]))

(defn form [& items]
  (into [space {:direction "vertical" :style {:width "100%"}}] items))

(defn form-item [{:keys [label control]}]
  [:div {:style {:display "grid"
                 :grid-template-columns "1fr 1fr"
                 :align-items "center"}}
   [typography label]
   control])

;; todo: comment
(defn- popover-children [props]
  (reag/as-element (. props (children (. props -onClick)))))

(defn popover [trigger content]
  [:> antd/Popover {"content" (reag/as-element content)
                    "trigger" "click"
                    "placement" "bottom"}
   [:> popover-children {:children trigger}]])

(defn custom-popover []
  (let [!opened (reag/atom false)]
    (fn [trigger over]
      [:div {:style {:position "relative"}}
       (trigger (fn [] (reset! !opened true)))
       (when @!opened
         [:div
          [:div {:style {:position "fixed"
                         :z-index 100
                         :top "0px"
                         :right "0px"
                         :bottom "0px"
                         :left "0px"}
                 :on-click (fn []
                             (reset! !opened false))}]
          [:div {:style {:position "absolute" :z-index 101 :bottom "calc(100% + 5px)"}}
           (over (fn [] (reset! !opened false)))]])])))

(defn slider [{:keys [value label block min max step style on-change data-testid]}]
  [:div (merge {:style {:display :flex
                        :align-items :center
                        :width (if block "100%" "250px")
                        :gap "8px"
                        :font-size "13px"}}
               (when data-testid {:data-testid data-testid}))
   [:div {:style {:display :flex :gap "4px"}}
    [typography label]
    [typography {:style {:white-space "nowrap" :width "20px"}} ;; fixed width is used to avoid slider jumping when value width is changed
     (str "(" value ")")]]
   [:> antd/Slider {:value value
                    :min min
                    :max max
                    :step (or step 1)
                    :style (merge {:user-select "none" :flex-grow 1} style)
                    :onChange (fn [value]
                                (on-change value))}]])

(defn checkbox [{:keys [value on-change label data-testid]}]
  [:> antd/Checkbox {:checked value
                     :data-testid data-testid
                     :onChange (fn [e]
                                 (on-change (.. e -target -checked)))}
   label])

(defn radio-group [{:keys [value options on-change]}]
  (into
   [:> (.-Group antd/Radio) {:value (name value)
                             :onChange (fn [e]
                                         (on-change (keyword (.. e -target -value))))}]
   (map (fn [opt] [:> (.-Button antd/Radio) {:value (name (:value opt))} (:label opt)]) options)))

(defn button [{:keys [on-click disabled data-testid]} text]
  [:> antd/Button (cond-> {:onClick on-click :disabled disabled}
                    data-testid (assoc :data-testid data-testid))
   text])

(defn select [{:keys [value size on-change block options data-testid]}]
  (reag/with-let [!ref (atom nil)]
    (let [options-with-idx (map-indexed (fn [idx v] (assoc v :value idx)) options) ;; todo: we cannot use cljs object as value so we need this hack with index
          value-idx (.indexOf (mapv :value options) value)]
      [:> antd/Select {:value value-idx
                       :ref (fn [ref] (reset! !ref ref))
                       :options (clj->js options-with-idx)
                       :data-testid data-testid
                       :size (case size
                               :sm "small"
                               :lg "large"
                               :md "middle"
                               nil)
                       :style {:width (when block "100%")}
                       :on-change (fn [_ option]
                                    ;; after select option, select has focus and pressing hotkeys doesn't work + any key lead to select opening
                                    ;; todo: find better way?
                                    (when-let [value (some-> (nth options (.-value option)) :value)]
                                      (.. @!ref blur)
                                      (on-change value)))}])))

(defn icon-button [{:keys [src icon-theme title active disabled size on-click data-testid]}]
  [:button (merge {:className "icon-button"
                   :style (merge
                           {:border "none"
                            :outline "none"
                            :padding 0
                            :background-color (if active "rgba(255,255,255,.2)" "transparent")
                            :border-radius "4px"
                            :opacity (if disabled "0.4" 1)
                            :cursor (if disabled "default" "pointer")}
                           (cond
                             (= size :sm)
                             {:width "28px" :height "28px" :min-height "28px" :min-width "28px"}
                             (= size :xs)
                             {:width "18px" :height "18px" :min-height "18px" :min-width "18px"}
                             :else
                             {:width "100%" :height "100%"}))
                   :title title
                   :data-testid data-testid
                   :disabled disabled
                   :on-click on-click})
   [:div {:style {:width "100%"
                  :height "100%"
                  :mask-image (str "url(./imgs/" (name src) ".svg)")
                  :mask-repeat "no-repeat"
                  :mask-position "center"
                  :mask-size "70%"
                  :background-color (case (or icon-theme :light)
                                      :light "white"
                                      :dark "black")}}]])

;; todo: integer input number
(def-func-component input-number [{:keys [value min max block on-blur testid]}]
  (let [[curr-value set-curr-value] (react/useState value)]
    (react/useEffect (fn []
                       (set-curr-value value))
                     (array value))
    [:> antd/InputNumber {:value curr-value
                          :step 1
                          :data-testid testid
                          :style {:width (when block "100%")}
                          :onChange (fn [value]
                                      (set-curr-value value))
                          :onBlur (fn []
                                    (let [new-value (let [res (. js/Number (parseInt curr-value))]
                                                      (if (js/isNaN res)
                                                        nil
                                                        (cond-> res
                                                          (some? min) (clojure.core/max min)
                                                          (some? max) (clojure.core/min max))))]
                                      (set-curr-value new-value)
                                      (on-blur new-value)))}]))

(def-func-component input-text [{:keys [value on-blur testid]}]
  (let [[curr-value set-curr-value] (react/useState value)]
    (react/useEffect (fn []
                       (set-curr-value value))
                     (array value))
    [:> antd/Input {:value curr-value
                    :data-testid testid
                    :onChange (fn [e]
                                (set-curr-value (.. e -target -value)))
                    :onBlur (fn []
                              (on-blur curr-value))
                    :onPressEnter (fn []
                                    (on-blur curr-value))}]))

(defn modal [props & children]
  (let [{:keys [on-cancel cancel-text on-ok ok-text ok-disabled hide-footer title additional-buttons ok-data-testid]} props]
    (into
     [:> antd/Modal (merge
                     {:title title
                      :open true
                      :closable true
                      :width (case (:size props)
                               :lg "50%"
                               :md "30%"
                               :sm "18%"
                               :else nil)
                      :onOk on-ok
                      :okText ok-text
                      :okButtonProps (cond-> {:disabled ok-disabled}
                                      ok-data-testid (assoc :data-testid ok-data-testid))
                      :onCancel on-cancel
                      :cancelText cancel-text
                      :footer (fn [_ props]
                                (reag/as-element
                                 (into [:div {:style {:display :flex :gap "6px"}}]
                                       (concat
                                        [(into [:div {:style {:display "flex" :gap "6px" :margin-right "auto"}}]
                                               additional-buttons)]
                                        [[:> (. props -CancelBtn)]
                                         [:> (. props -OkBtn)]]))))}
                     (when hide-footer {:footer nil}))]
     children)))

(defn file-uploader [{:keys [on-upload accept data-testid]} render-button]
  (reag/with-let [!input-ref (reag/atom nil)]
    [:span
     [:input {:type "file"
              :accept accept
              :data-testid data-testid
              :ref (fn [ref] (reset! !input-ref ref))
              :style {:display "none"}
              :on-change (fn [e]
                           (let [files (.. e -target -files)]
                             (when-first [file files]
                               (let [file-reader (js/FileReader.)]
                                 (set! (. file-reader -onload) (fn [e]
                                                                 (on-upload {:file-name (. file -name)
                                                                             :content (.. e -target -result)})))
                                 (. file-reader (readAsText file)))))
                           (set! (.. e -target -value) "") ;; without this line onChange is not triggered when the same file is choosen twice
                           )}]
     [render-button (fn []
                      (.. @!input-ref click))]]))

(defn replace-current-project-confirm []
  (js/confirm "This will replace the current project. Are you sure?"))
