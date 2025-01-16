(ns pixel-art.views.reagent)

;; todo: add comment why we need it
(defmacro def-func-component [comp-name args body]
  (let [real-comp-name# (symbol (str comp-name "-component"))]
    `(do
       (defn ~real-comp-name# ~args ~body)
       (def ~comp-name
         (fn [props#]
           [:f> ~real-comp-name# props#])))))
