(ns pixel-art.tool.shape
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                 with-highlight-cel-under-cursor]]))

(defn make [{:keys [type options-spec get-points]}] ;; todo: draw -> get-points
  (let [init (fn [] {:type type})]
    {:type type
     :init init
     :options-spec options-spec
     :get-events-handlers
     (fn []
       (with-highlight-cel-under-cursor
         {:mouse-down-or-mouse-down-and-move
          (fn [db event]
            {:db db
             :fx [[:clear-preview]
                  [:draw-preview (get-points db event)]]})
          :mouse-up
          (fn [db event]
            (let [rectangle-image (get-points db event)]
              (commit-changes-and-init-tool db rectangle-image (init))))}))}))
