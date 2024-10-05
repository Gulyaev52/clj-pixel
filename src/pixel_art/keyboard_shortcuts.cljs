(ns pixel-art.keyboard-shortcuts
  (:require [pixel-art.tool.core :as tool]
            [clojure.set :as set]
            [pixel-art.tool.rectangle-select :as selection]
            [pixel-art.history.events :as history]
            [clojure.string :as string]))

(def shortcuts-by-types
  {:tools (->> [[:pen {:key "p"}]
                [:bucket {:key "b"}]
                [:eraser {:key "e"}]
                [:line {:key "l"}]
                [:circle {:key "c"}]
                [:rectangle {:key "r"}]
                [:color-picker {:key "o"}]
                [:shading {:key "u"}]
                [:shape-select {:key "z"}]
                [:rectangle-select {:key "s"}]]
               (map (fn [[tool keys]]
                      {:label (string/replace (name tool) #"\-" " ")
                       :keys [keys]
                       :tool tool
                       :action [:pixel-art.events/select-tool tool]})))
   :misc [{:label "undo"
           :keys [{:key "z"
                   :ctrlKey true}]
           :action [::history/undo]}
          {:label "redo"
           :keys [{:key "y"
                   :ctrlKey true}]
           :action [::history/redo]}]
   :selection [{:label "cut selection"
                :keys [{:key "x"}]
                :action [::selection/cut-selection]}
               {:label "copy selection"
                :keys [{:key "c"
                        :ctrlKey true}]
                :action [::selection/copy-selection]}
               {:label "past selection"
                :keys [{:key "v"
                        :ctrlKey true}]
                :action [::selection/past-selection]}
               {:label "delete selection"
                :keys [{:key "delete"}
                       {:key "backspace"}]
                :action [::selection/delete-selection]}
               {:label "commit selection"
                :keys [{:key "enter"}]
                :action [::selection/commit-selection]}]})
(let [diff (set/difference (set tool/types) (set (map :tool (:tools shortcuts-by-types))))]
  (assert (= diff #{}) (str (vec diff) " don't have hotkeys")))

(def key->code
  {"p" 80
   "b" 66
   "e" 69
   "l" 76
   "c" 67
   "r" 82
   "o" 79
   "u" 85
   "z" 90
   "s" 83
   "y" 89
   "x" 88
   "v" 86
   "delete" 46
   "backspace" 8
   "enter" 13})
(let [diff (set/difference (set (map :key (mapcat :keys (flatten (vals shortcuts-by-types)))))
                           (set (keys key->code)))]
  (assert (= diff #{}) (str (vec diff) " don't have hotkeys")))

(defn keys->string [keys]
  (->> keys
       (map (fn [{:keys [ctrlKey shiftKey key]}]
              (->> [(when ctrlKey "ctrl") (when shiftKey "shift") key]
                   (keep identity)
                   (string/join " + "))))
       (string/join " / ")))
