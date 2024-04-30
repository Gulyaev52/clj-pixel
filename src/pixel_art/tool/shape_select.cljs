(ns pixel-art.tool.shape-select
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.tool.utils :refer [commit-preview-changes]]))

(defn init [] {:type :shape-select :state {:mode :select}})

(def options-spec
  [])

(defn unselect [db] (commit-preview-changes db))

(defn- remove-transparent-colors [selection-image]
  (->> selection-image
       (filter (fn [[_ color]] (not= color frame/transparent-color)))
       (into {})))

;; todo: поменять порядок арг
(defn handle-mouse-event [event db]
  (let [{:keys [tool source-frame initial-mouse-down-pos user-is-drawing]} db]
    (case (-> tool :state :mode)
      :select

      (= (:type event) :mouse-move)
      {:db db
       :highlight-pixels [(:pos event)]})))
