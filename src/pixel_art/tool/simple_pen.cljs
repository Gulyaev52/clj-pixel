(ns pixel-art.tool.simple-pen
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool get-tool-options
                                 resize-pixel]])) ;; todo: rename

(defn make [{:keys [type options-spec draw]}]
  (let [init (fn init [] {:type type
                          :state {:changes {}
                                  :prev-pos nil}})]
    {:type type
     :init init
     :options-spec options-spec
     :handle-mouse-event
     (fn [db event]
       (let [{:keys [user-is-drawing]} db]
         (cond
           (or (= (:type event) :mouse-down)
               (and (= (:type event) :mouse-move) user-is-drawing))
           (let [new-pixels (draw db event)
                 prev-changes (-> db :tool :state :changes)
                 updated-state {:changes (merge prev-changes new-pixels)
                                :prev-pos (:pos event)}]
             {:db (assoc-in db [:tool :state] updated-state)
              :fx [[:clear-visual-effects]
                   [:draw-preview new-pixels]]})

           (and (= (:type event) :mouse-move) (not user-is-drawing))
           (let [{:keys [pixel-size]} (get-tool-options db)
                 points (resize-pixel (:pos event) pixel-size)]
             {:db db
              :fx [[:clear-visual-effects]
                   [:highlight-pixels points]]})

           (= :mouse-up (:type event))
           (let [changes (-> db :tool :state :changes)]
             (commit-changes-and-init-tool db changes (init))))))}))
