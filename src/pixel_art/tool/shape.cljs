(ns pixel-art.tool.shape
  (:require
   [pixel-art.tool.utils :refer [commit-preview-and-init-tool
                                 with-highlight-cel-under-cursor]]))

(defn make [{:keys [type options-spec draw]}]
  (let [init (fn [] {:type type})]
    {:type type
     :init init
     :options-spec options-spec
     :get-events-handlers
     (fn []
       (with-highlight-cel-under-cursor
         {:mouse-down-or-mouse-down-and-move
          (fn [db event]
            {:db (assoc db :preview (draw db event))})
          :mouse-up
          (fn [db event]
            (let [preview (draw db event)]
              (commit-preview-and-init-tool db preview (init))))}))}))
