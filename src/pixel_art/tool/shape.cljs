(ns pixel-art.tool.shape
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool make-default-handle-mouse-event]]))

(defn make [{:keys [type options-spec draw]}] ;; todo: draw -> get-points
  (let [init (fn [] {:type type})]
    {:type type
     :init init
     :options-spec options-spec
     :handle-mouse-event
     (make-default-handle-mouse-event
      {:mouse-down-or-mouse-down-and-move
       (fn [db event]
         {:db db
          :fx [[:draw-preview (draw db event)]]})
       :mouse-up
       (fn [db event]
         (let [rectangle-image (draw db event)]
           (commit-changes-and-init-tool db rectangle-image (init))))})}))
