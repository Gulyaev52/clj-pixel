(ns pixel-art.views.reagent)

(defmacro def-func-component [comp-name args body]
  (let [real-comp-name# (symbol (str comp-name "-component"))]
    `(do
       (defn ~real-comp-name# ~args ~body)
       (def ~comp-name
         (fn [props# & args#]
           (into [:f> ~real-comp-name# props#] args#))))))

(defmacro for-loop [[sym init check change :as params] & steps]
  `(loop [~sym ~init value# nil]
     (if ~check
       (let [new-value# (do ~@steps)]
         (recur ~change new-value#))
       value#)))
