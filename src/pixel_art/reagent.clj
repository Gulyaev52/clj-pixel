(ns pixel-art.reagent)

;; todo: add comment why we need it
(defmacro def-func-component [comp-name args body]
  (let [f# `(fn ~(symbol comp-name) ~args ~body)]
    `(def ~comp-name
       (fn [props#]
         [:f> ~f# props#]))))
