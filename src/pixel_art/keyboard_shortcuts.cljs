(ns pixel-art.keyboard-shortcuts
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [pixel-art.history.events :as history]
   [pixel-art.project-save-load.events :as project-save-load.events]
   [pixel-art.tool.core :as tool]
   [pixel-art.tool.selection :as tool.selection]))

(def shortcuts-by-types
  {:tools (->> [[:pen {:key "p"}]
                [:bucket {:key "b"}]
                [:eraser {:key "e"}]
                [:line {:key "l"}]
                [:circle {:key "c"}]
                [:rectangle {:key "r"}]
                [:color-picker {:key "o"}]
                [:shading {:key "u"}]
                [:shape-selection {:key "z"}]
                [:rectangle-selection {:key "s"}]]
               (map (fn [[tool keys]]
                      {:label (string/replace (name tool) #"\-" " ")
                       :keys [keys]
                       :tool tool
                       :action [:pixel-art.events/select-tool tool]})))
   :misc [{:label "undo"
           :keys [{:key "z"
                   :ctrlKey true}]
           :action [::history/undo]
           :prevent-default-keys true}
          {:label "redo"
           :keys [{:key "y"
                   :ctrlKey true}]
           :action [::history/redo]
           :prevent-default-keys true}
          {:label "save as file"
           :keys [{:key "s"
                   :ctrlKey true}]
           :action [::project-save-load.events/save-as-file]
           :prevent-default-keys true}]
   :selection [{:label "cut selection"
                :keys [{:ctrlKey true :key "x"}]
                :action [::tool.selection/cut-selection]
                :prevent-default-keys true}
               {:label "copy selection"
                :keys [{:key "c"
                        :ctrlKey true}]
                :action [::tool.selection/copy-selection]
                :prevent-default-keys true}
               {:label "past selection"
                :keys [{:key "v"
                        :ctrlKey true}]
                :action [::tool.selection/past-selection]
                :prevent-default-keys true}
               {:label "delete selection"
                :keys [{:key "delete"}
                       {:key "backspace"}]
                :action [::tool.selection/delete-selection]
                :prevent-default-keys true}
               {:label "commit selection"
                :keys [{:key "enter"}]
                :action [::tool.selection/commit-selection]
                :prevent-default-keys true}]})
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
