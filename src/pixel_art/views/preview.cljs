(ns pixel-art.views.preview
  (:require
   [react :as react]
   [pixel-art.views.ui-kit :refer [typography]]
   [shadow.css :refer (css)])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def-func-component preview-image [src style]
  (let [container-ref (react/useRef nil)]
    (react/useEffect (fn []
                       (when-let [container (.-current container-ref)]
                         (set! (. container -onload)
                               (fn []
                                 (when-let [container (.-current container-ref)]
                                   (if (and (< (.-naturalWidth container)
                                               (.-offsetWidth container))
                                            (< (.-naturalHeight container)
                                               (.-offsetHeight container)))
                                     (set! (.. container -style -imageRendering) "pixelated")
                                     (set! (.. container -style -imageRendering) "auto")))))))
                     (array container-ref src))
    [:img {:src src
           :ref container-ref
           :class (css {:position "relative"
                        :image-rendering "pixelated"
                        :background-image "var(--pixel-transparent-img)"
                        :border "var(--pixel-drawing-border)"})
           :style style}]))

(defn previews-container [{:keys [loading]} items]
  [:div {:class (css {:display "flex"
                      :flex-wrap "wrap"
                      :justify-content "center"
                      :justify-items "center"
                      :width "100%"
                      :height "200px"
                      :border "1px solid black"
                      :padding "2px"
                      :overflow "auto"
                      :background-color "var(--pixel-preview-bg)"})
         :style {:opacity (when loading "0.6")}}
   items])

(defn previews-grid-items [previews]
  (if (= (count previews) 1)
    [:div {:data-testid "preview-frame-0"
           :class (css {:height "100%"})}
     [preview-image (first previews)
      {:height "100%"
       :min-height "70px"}]]

    [:<>
     (for [[idx data-url] (map-indexed vector previews)]
       ^{:key idx}
       [:div {:data-testid (str "preview-frame-" idx)
              :class (css {:display "flex"
                           :flex-direction "column"
                           :height "100%"
                           :align-items "center"
                           :min-width "0"})}
        [preview-image data-url
         {:height "100%"
          :min-height "70px"}]
        [:div {:class (css {:padding "5px"})}
         [typography (inc idx)]]])]))
