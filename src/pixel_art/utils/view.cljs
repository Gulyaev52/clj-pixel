(ns pixel-art.utils.view)

(defn is-right-button? [e]
  (= (. e -button) 2))

(defn is-middle-button? [e]
  (= (. e -button) 1))

(defn get-mouse-client-pos [e]
  {:x (. e -clientX) :y (. e -clientY)})

;; todo: move to another ns
(defn debounce
  "Создает функцию, которая откладывает вызов функции `f` до тех пор, пока не пройдет `wait` миллисекунд после последнего вызова."
  [f wait]
  (let [timeout-id (atom nil)]
    (fn [& args]
      (when-let [tid @timeout-id]
        (js/clearTimeout tid))
      (reset! timeout-id
              (js/setTimeout
               (fn []
                 (apply f args))
               wait)))))
