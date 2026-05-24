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
  (let [route      (:route @app-state)
        route-name (get-in route [:data :name])
        params     (:path-params route)]
    (case route-name
      :home
      [home-page]

      :grade
      [grade-page (:grade params)]

      :lesson
      [lesson-page (:grade params) (:id params)]

      :quiz
      [quiz-page (:grade params) (:id params)]

      [:div.page [:p "Page not found."]])))

(defn app-root []
  [:<>
   [nav]
   [current-page]])

(defn ^:export init []
  (init-router!)
  (rdom/render [app-root]
               (.getElementById js/document "app")))
