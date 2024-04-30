(ns pixel-art.tool.pen
  (:require [pixel-art.tool.common :refer [update-preview-and-draw commit-preview-changes]]))

;; todo: использовать полиморфизм?
(defn init [] {:type :pen})

(defn handle-mouse-event [event db]
  (def event event)
  (def db db)
  (let [{:keys [preview color]} db]
    (cond
      (#{:mouse-down :mouse-move} (:type event))
      (let [preview (assoc preview (:pos event) color)]
        (update-preview-and-draw db preview {:clear false}))

      (= :mouse-up (:type event))
      (commit-preview-changes db))))
