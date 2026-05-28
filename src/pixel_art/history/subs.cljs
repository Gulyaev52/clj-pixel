(ns pixel-art.history.subs
  (:require
   [pixel-art.history :as history]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::undo-available?
 history/check-undo-available?)

(re-frame/reg-sub
 ::redo-available?
 history/check-redo-available?)
