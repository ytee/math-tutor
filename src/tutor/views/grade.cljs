(ns tutor.views.grade
  (:require [tutor.state  :refer [app-state]]
            [tutor.router :refer [href]]))

;; Static lesson index per grade.
;; Later this can be loaded from a grade-index.edn file.
(def lesson-index
  {"grade1" [{:id "addition"    :title "Addition"}
             {:id "subtraction" :title "Subtraction"}]
   "grade2" [{:id "multiplication" :title "Multiplication"}
             {:id "place-value"    :title "Place value"}]
   "grade5" [{:id "fractions"   :title "Fractions"}
             {:id "decimals"    :title "Decimals"}]})

(defn grade-page []
  (let [grade (get-in @app-state [:route :path-params :grade])
        lessons (get lesson-index grade [])]
    [:div.page
     [:h1 (str "Grade: " grade)]
     [:p "Pick a lesson to begin."]
     (if (empty? lessons)
       [:p.error "No lessons found for this grade."]
       [:ul.lesson-list
        (for [{:keys [id title]} lessons]
          ^{:key id}
          [:li
           [:a {:href (href :lesson {:grade grade :id id})} title]
           [:a.btn.btn-outline
            {:href (href :quiz {:grade grade :id id})}
            "Quiz"]])])]))
