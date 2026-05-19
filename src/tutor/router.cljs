(ns tutor.router
  (:require [reitit.frontend       :as rf]
            [reitit.frontend.easy  :as rfe]
            [tutor.state           :refer [app-state]]))

(def routes
  [["/"                    {:name :home}]
   ["/grade/:grade"        {:name :grade}]
   ["/lesson/:grade/:id"   {:name :lesson}]
   ["/quiz/:grade/:id"     {:name :quiz}]])

(defn href
  "Build a hash href for a named route with optional params.
   Usage: (href :lesson {:grade \"grade1\" :id \"addition\"})"
  ([name]        (rfe/href name))
  ([name params] (rfe/href name params)))

(defn navigate! [name params]
  (rfe/push-state name params))

(defn init-router! []
  (rfe/start!
    (rf/router routes)
    (fn [match _history]
      (swap! app-state assoc
             :route   match
             :lesson  nil      ;; clear stale lesson on navigation
             :answers {}
             :error   nil))
    {:use-fragment true}))    ;; #/lesson/... — works on all static hosts
