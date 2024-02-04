(ns let-script
  (:require ["vscode" :as vscode]
            ["ext://betterthantomorrow.calva$v0" :as calva]
            [clojure.walk]
            [clojure.string]
            [promesa.core :as p]
            [joyride.core :as joyride]))

(defn current-document []
  (let [editor ^js vscode/window.activeTextEditor
        document (.-document editor)]
    document))

(defn current-selection []
  (let [editor ^js vscode/window.activeTextEditor
        selection (.-selection editor)]
    selection))

(defn current-selection-text []
  (.getText (current-document) (current-selection)))

(defn get-symbols [form]
  (let [symbols (atom [])]
    (->> form
         read-string
         (partition 2)
         (map first)
         (map #(clojure.walk/postwalk (fn [n]
                                        (when (symbol? n)
                                          (swap! symbols (fn [s]
                                                           (conj s n)))))
                                      %))
         doall)
    @symbols))

(defn insert-text!+
  ([^js text]
   (insert-text!+ text vscode/window.activeTextEditor (.-active (current-selection))))
  ([text ^js editor ^js position]
   (-> (p/do (.edit editor
                    (fn [^js builder]
                      (.insert builder position text))
                    #js {:undoStopBefore true :undoStopAfter false}))
       (p/catch (fn [e]
                  (js/console.error e))))))

(defn define-symbols [text]
  (let [symbols (get-symbols (str "[" text "]"))
        def-symbols (->> (map (fn [s] (str "(def " s " " s ")")) symbols)
                         (clojure.string/join "\n"))
        result (str "(let [" text "]\n" def-symbols ")")]
    #_(doto (joyride/output-channel)
        (.show true)
        (.appendLine result))
    (insert-text!+ result)))


(when (= (joyride/invoked-script) joyride/*file*)
  (-> (define-symbols (current-selection-text))
      (.then (fn [_]
               (vscode/window.showInformationMessage "success")))
      (.catch #(vscode/window.showErrorMessage %))))

(comment
  (define-symbols "{:keys [a b]} {:a 1 :b 2}
                   [head tail] [1 2]
                   val 11111"))
