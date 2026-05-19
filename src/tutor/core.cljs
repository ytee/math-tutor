(ns tutor.core
  (:require [reagent.dom            :as rdom]
            [tutor.router           :refer [init-router!]]
            [tutor.state            :refer [app-state]]
            [tutor.components.nav   :refer [nav]]
            [tutor.views.home       :refer [home-page]]
            [tutor.views.grade      :refer [grade-page]]
            [tutor.views.lesson     :refer [lesson-page]]
            [tutor.views.quiz       :refer [quiz-page]]))

(defn current-page []
  (let [route-name (get-in @app-state [:route :data :name])]
    (case route-name
      :home   [home-page]
      :grade  [grade-page]
      :lesson [lesson-page]
      :quiz   [quiz-page]
      [:div.page [:p "Page not found."]])))

(defn app-root []
  [:<>
   [nav]
   [current-page]])

(defn ^:export init []
  (init-router!)
  (rdom/render [app-root]
               (.getElementById js/document "app")))
