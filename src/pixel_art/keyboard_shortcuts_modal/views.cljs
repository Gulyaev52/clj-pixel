(ns pixel-art.keyboard-shortcuts-modal.views
  (:require
   [clojure.string :as string]
   [pixel-art.keyboard-shortcuts :as keyboard-shortcuts]
   [pixel-art.keyboard-shortcuts-modal.events :as events]
   [pixel-art.keyboard-shortcuts-modal.subs :as subs]
   [pixel-art.views.ui-kit :refer [modal title]]
   [re-frame.core :as re-frame]
   [shadow.css :refer (css)]))

(defn keyboard-shortcuts-modal []
  (when @(re-frame/subscribe [::subs/opened])
    [modal {:title "Keyboard shortcuts"
            :size :lg
            :hide-footer true
            :on-cancel (fn []
                         (re-frame/dispatch [::events/set-opened false]))}
     [:div {:class (css {:display "grid"
                         :grid-template-columns "repeat(auto-fit, minmax(200px,1fr))"})}

      (for [[type shortcuts] keyboard-shortcuts/shortcuts-by-types]
        ^{:key (name type)}
        [:div
         [title {:level 4} (str (string/capitalize (name type)) " shortcuts")]
         [:div
          (for [shortcut shortcuts]
            ^{:key (:label shortcut)}
            [:div (string/capitalize (:label shortcut))
             " - "
             (keyboard-shortcuts/keys->string (:keys shortcut))])]])]]))
