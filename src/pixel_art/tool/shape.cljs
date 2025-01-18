(ns pixel-art.tool.shape
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool get-tool-options
                                 resize-pixel]]))

(defn make [{:keys [type options-spec draw]}] ;; todo: draw -> get-points
  (let [init (fn [] {:type type})]
    {:type type
     :init init
     :options-spec options-spec
     :handle-mouse-event
     (fn handle-mouse-event [db event]
       (let [{:keys [user-is-drawing]} db]
         (cond
           (or (= (:type event) :mouse-down)
               (and (= (:type event) :mouse-move) user-is-drawing)) ;; todo: to predicate
           {:db db
            :fx [[:clear-preview]
                 [:clear-visual-effects]
                 [:draw-preview (draw db event)]]}

           (and (= (:type event) :mouse-move) (not user-is-drawing)) ;; todo: to predicate
           (let [{:keys [pixel-size]} (get-tool-options db)
                 points (resize-pixel (:pos event) pixel-size)]
             {:db db
              :fx [[:clear-visual-effects]
                   [:highlight-pixels points]]})

           (= :mouse-up (:type event))
           (let [rectangle-image (draw db event)]
             (commit-changes-and-init-tool db rectangle-image (init))))))}))
