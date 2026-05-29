(ns tutor.views.quiz
  (:require [tutor.state            :refer [app-state]]
            [tutor.router           :refer [href]]
            [tutor.components.math  :refer [render-math]]
            [tutor.views.lesson     :refer [load-lesson!]]))

;; ── State helpers ─────────────────────────────────────────────────────────────

(defn- answer! [q-id chosen correct-answer]
  (swap! app-state update :answers assoc q-id
         {:chosen   chosen
          :correct? (= chosen correct-answer)}))

;; ── Option generation for exercise-based quizzes ─────────────────────────────

(defn- generate-numeric-options
  "Generate 4 plausible multiple-choice options for a numeric answer.
   Ensures correct answer is included, all values ≥ 0, and no duplicates."
  [answer]
  (let [candidates (distinct
                    (filter #(>= % 0)
                            [(- answer 2) (- answer 1) answer
                             (+ answer 1) (+ answer 2) (+ answer 3)]))
        ;; ensure correct answer is first, then pick 3 others
        others    (take 3 (remove #{answer} candidates))
        options   (cons answer others)]
    (vec (shuffle options))))

(defn- exercises->quiz-questions
  "Convert exercise-set items into quiz questions.
   Numeric exercises get generated multiple-choice options."
  [exercise-sets]
  (vec
   (for [es    exercise-sets
         item  (:exercise-set/items es)
         :let  [answer (:exercise/answer item)]
         :when (some? answer)]
     {:id       (:exercise/id item)
      :question (:exercise/prompt item)
      :options  (generate-numeric-options answer)
      :answer   answer
      :math?    false})))

;; ── Question component ────────────────────────────────────────────────────────

(defn- question-view [{:keys [id question options answer math?]}]
  (let [result (get-in @app-state [:answers id])]
    [:div.question-block
     [:p.question-text
      (if math?
        [render-math question]
        question)]
     (for [opt options]
       ^{:key (str id "-" opt)}
       [:button.quiz-option
        {:class    (when result
                     (cond
                       (= opt answer)          "correct"
                       (= opt (:chosen result)) "wrong"))
         :disabled (boolean result)
         :on-click #(answer! id opt answer)}
        (str opt)])]))

;; ── Page ─────────────────────────────────────────────────────────────────────

(defn quiz-page [grade id]
  (let [loaded-key (atom nil)]
    (fn [grade id]
      (let [key (str grade "/" id)]
        (when (not= @loaded-key key)
          (reset! loaded-key key)
          (js/setTimeout #(load-lesson! grade id) 0)))

      (let [{:keys [lesson exercise-sets loading error answers]} @app-state

            ;; Build quiz questions from whichever format is loaded
            qs (cond
                 ;; Legacy format: lesson has :quiz key with ready-made questions
                 (seq (:quiz lesson))
                 (mapv #(assoc % :math? true) (:quiz lesson))

                 ;; Compiled format: generate quiz from exercise-sets
                 (seq exercise-sets)
                 (exercises->quiz-questions exercise-sets)

                 :else [])

            answered (count answers)
            total    (count qs)
            score    (->> answers vals (filter :correct?) count)
            done?    (and (pos? total) (= answered total))]

        [:div.page
         (cond
           loading [:p.loading "Loading quiz…"]
           error   [:p.error   (str "Error: " error)]

           (and lesson (seq qs))
           [:<>
            [:h1 (str "Quiz: " (or (:lesson/title lesson) (:title lesson)))]
            (for [q qs]
              ^{:key (:id q)} [question-view q])

            (when done?
              [:div.score-card
               [:h2 (str "Score: " score " / " total)]
               (if (= score total)
                 [:p "Perfect! Outstanding work."]
                 [:p (str score " correct. Review the lesson and try again.")])
               [:div {:style {:display "flex" :gap "1rem" :justify-content "center"
                              :margin-top "1rem"}}
                [:a.btn {:href (href :lesson {:grade grade :id id})}
                 "Review lesson"]
                [:a.btn.btn-outline {:href (href :grade {:grade grade})}
                 "All lessons"]]])]

           lesson
           [:div.page
            [:h1 (str "Quiz: " (or (:lesson/title lesson) (:title lesson)))]
            [:p "No quiz available for this lesson yet."]]

           :else [:p.loading "Loading…"])]))))
