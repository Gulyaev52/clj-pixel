(ns pixel-art.measure)

(defn measure-fn [label fn]
  (let [start (. js/Date now)
        res (fn)
        end (. js/Date now)]
    (println (str label ": " (- end start) "ms"))
    res))
