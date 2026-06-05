(ns tutor.views.home
  (:require [tutor.router :refer [href]]))

(def grades
  [{:id "grade1" :label "Grade 1"  :desc "Counting, addition, subtraction"}
   {:id "grade2" :label "Grade 2"  :desc "Place value, multiplication intro"}
   {:id "grade5" :label "Grade 5"  :desc "Fractions, decimals, percentages"}
   {:id "grade6" :label "Grade 6"  :desc "Patterns, number sequences, geometry"}])

(defn grade-href [id]
  (case id
    "grade1" "#/lesson/grade1/addition"
    "grade6" "#/lesson/grade6/patterns.intro"
    (href :grade {:grade id})))

(defn home-page []
  [:div.page
   [:h1 "Welcome to Math Tutor"]
   [:p "Choose a grade to start learning."]
   [:div.grade-grid
    (for [{:keys [id label desc]} grades]
      ^{:key id}
      [:a.grade-card {:href (grade-href id)}
       [:h3 label]
       [:p desc]])]])