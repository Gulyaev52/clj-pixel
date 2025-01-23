(ns pixel-art.tool.simple-pen
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                 with-highlight-cel-under-cursor]])) ;; todo: rename

(defn make [{:keys [type options-spec draw]}]
  (let [init (fn init [] {:type type
                          :state {:changes {}
                                  :prev-pos nil}})]
    {:type type
     :init init
     :options-spec options-spec
     :get-events-handlers
     (fn []
       (with-highlight-cel-under-cursor
         {:mouse-down-or-mouse-down-and-move
          (fn [db event]
            (let [new-pixels (draw db event)
                  prev-changes (-> db :tool :state :changes)
                  tool-updated-state {:changes (merge prev-changes new-pixels)
                                      :prev-pos (:pos event)}]
              {:db (assoc-in db [:tool :state] tool-updated-state)
               :fx [[:draw-preview new-pixels]]}))
          :mouse-up (fn [db]
                      (let [changes (-> db :tool :state :changes)]
                        (commit-changes-and-init-tool db changes (init))))}))}))
