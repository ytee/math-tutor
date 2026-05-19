(ns tutor.components.nav
  (:require [tutor.router :refer [href]]))

(defn nav []
  [:nav
   [:a.brand {:href (href :home)} "Math Tutor"]
   [:a {:href (href :home)} "Home"]])
