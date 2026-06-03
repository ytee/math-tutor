(ns tutor.state
  (:require [reagent.core :as r]))

(defonce app-state
  (r/atom {:route         nil      ;; current Reitit match
           :lesson        nil      ;; loaded lesson map (old or new format)
           :exercise-sets []       ;; exercise-sets from compiled bundle
           :answers       {}       ;; quiz-id → {:chosen x :correct? bool}
           :results       {}       ;; exercise-id → bool (inline exercises)
           :loading       false
           :error         nil}))
