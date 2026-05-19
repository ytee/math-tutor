(ns tutor.state)

(defonce app-state
  (atom {:route   nil      ;; current Reitit match
         :lesson  nil      ;; loaded EDN map for current lesson
         :answers {}       ;; quiz-id → {:chosen x :correct? bool}
         :loading false
         :error   nil}))
