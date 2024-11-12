(ns pixel-art.tool.shape-selection
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.tool.rectangle-selection :as rectangle-selection :refer [move-selection]]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-current-cel]]
            [pixel-art.utils.geometry :as geometry]))

;; подумать как работать с preview и canvas

(defn init [] {:type :shape-selection :state {:mode :select}})

(def options-spec
  [])

(defn commit-moved-selection [db]
  (let [changes (-> db :tool :state :changes)]
    (commit-changes-and-init-tool db changes (init))))

(defn handle-mouse-event [db event]
  (let [{:keys [tool initial-mouse-down-pos user-is-drawing]} db]
    (case (-> tool :state :mode)
      :select
      (cond
        (= (:type event) :mouse-down)
        (let [current-cel (get-current-cel db)
              color-under-mouse (cel/get-pixel (:pos event) current-cel)
              selection-image (->> (geometry/flood-fill (:pos event)
                                                        (:size current-cel)
                                                        (:pixels current-cel)
                                                        color-under-mouse)
                                   (map (fn [p] [p color-under-mouse]))
                                   (into {}))
              tool (assoc tool :state {:mode :move-selection
                                       :initial-selection-image selection-image
                                       :selection-image selection-image
                                       :changes []})]
          {:db (assoc db :tool tool)
           :fx [[:clear-preview]
                [:highlight-selection selection-image]]})

        (= (:type event) :mouse-move)
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels [(:pos event)]]]}

        :else {:db db})

      :move-selection
      (cond
        (= (:type event) :mouse-down)
        (if (not (contains? (-> tool :state :selection-image) (:pos event)))
          (commit-moved-selection db)
          {:db (assoc-in db [:tool :state :user-is-moving-selection] true)})

        (and (or (= (:type event) :mouse-down)
                 (and (= (:type event) :mouse-move) user-is-drawing))
             (-> tool :state :user-is-moving-selection))
        (let [{:keys [changes]} (move-selection tool initial-mouse-down-pos event)]
          {:db (assoc-in db [:tool :state :changes] changes)
           :fx [[:clear-preview]
                [:draw-preview changes]]})

        (and (= (:type event) :mouse-up) (-> tool :state :user-is-moving-selection))
        (let [{:keys [changes moved-selection-image]} (move-selection tool initial-mouse-down-pos event)
              updated-tool (-> tool
                               (assoc-in [:state :selection-image] moved-selection-image)
                               (assoc-in [:state :changes] changes))]
          {:db (assoc db :tool updated-tool)
           :fx [[:clear-preview]
                [:draw-preview changes]
                [:highlight-selection moved-selection-image]]})

        :else {:db db}))))
