(ns pixel-art.tool.shading
  (:require ["tinycolor2" :as tinycolor]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-tool-options resize-pixel]]
            [pixel-art.model.color :as color]))

(defn init [] {:type :shading :state {:changes {}}})

(def options-spec
  [{:type :checkbox
    :field :lighten
    :label "Lighten"
    :initial-value true}
   {:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}
   {:type :slider
    :field :amount
    :label "Amount"
    :initial-value 6
    :min 1
    :max 100}])

(defn handle-mouse-event [db event]
  (let [{:keys [user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      (let [{:keys [pixel-size amount lighten]} (get-tool-options db)
            {:keys [sprite]} db
            current-cel (sprite/get-current-cel sprite)
            new-pixels (->> (resize-pixel (:pos event) pixel-size)
                            (keep (fn [pos]
                                    (let [color (cel/get-pixel pos current-cel)]
                                      (when-not (= color transparent-color)
                                        (let [tcolor (tinycolor color)]
                                          (if lighten
                                            (. tcolor (lighten amount))
                                            (. tcolor (darken amount)))
                                          [pos (color/rgba tcolor)]))))))]
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
