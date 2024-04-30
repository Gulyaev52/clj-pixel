(ns pixel-art.tool.common
  (:require [pixel-art.model.frame :as frame]
            [re-frame.db :as db]))

(defn update-preview-and-draw [db preview {:keys [clear]}]
  {:db (assoc db :preview preview)
   :draw-preview [preview {:clear clear}]})

(defn commit-preview-changes [db]
  (let [source-frame (frame/set-pixels-map (:preview db) (:source-frame db))]
    {:db (assoc db
                :preview nil
                :source-frame source-frame)
     :draw-preview [nil {:clear true}]
     :draw-frame source-frame}))

(defn get-tool-options [db]
  (get (db :tools-options) (-> db :tool :type)))
