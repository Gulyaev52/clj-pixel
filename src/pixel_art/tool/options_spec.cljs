(ns pixel-art.tool.options-spec)

(defn make-checkbox [{:keys [field label]}]
  {:type :checkbox
   :field field
   :initial-value false
   :label label})

(defn make-slider [{:keys [min max initial-value field label]}]
  {:type :slider
   :field field
   :label label
   :initial-value initial-value
   :min min
   :max max})

(def pixel-size (make-slider {:field :pixel-size
                              :label "Pixel size"
                              :initial-value 1
                              :min 1
                              :max 20}))
