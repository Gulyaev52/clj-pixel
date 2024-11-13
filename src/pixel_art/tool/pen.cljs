(ns pixel-art.tool.pen
  (:require [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-current-color get-tool-options
                                          resize-pixel]]))

(defn init [] {:type :pen :state {:changes {}}})

(def options-spec
  [{:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}])

(defn handle-mouse-event [db event]
  (let [{:keys [user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      (let [current-color (get-current-color db event)
            {:keys [pixel-size]} (get-tool-options db)
            new-pixels (->> (resize-pixel (:pos event) pixel-size)
                            (map (fn [p] [p current-color])))]
        {:db (update-in db [:tool :state :changes] #(merge % new-pixels))
         :fx [[:draw-preview new-pixels]]})

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels points]]})

      (= :mouse-up (:type event))
      (let [changes (-> db :tool :state :changes)]
        (commit-changes-and-init-tool db changes (init))))))
