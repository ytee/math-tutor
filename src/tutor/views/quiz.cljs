(ns tutor.views.quiz
  (:require [tutor.state            :refer [app-state]]
            [tutor.router           :refer [href]]
            [tutor.components.math  :refer [render-math]]
            [tutor.views.lesson     :refer [load-lesson!]]))

;; ── State helpers ─────────────────────────────────────────────────────────────

(defn answer! [q-id chosen correct-answer]
  (swap! app-state update :answers assoc q-id
         {:chosen   chosen
          :correct? (= chosen correct-answer)}))

;; ── Question component ────────────────────────────────────────────────────────

(defn question-view [{:keys [id question options answer]}]
  (let [result (get-in @app-state [:answers id])]
    [:div.question-block
     [:p.question-text [render-math question]]
     (for [opt options]
       ^{:key (str id "-" opt)}
       [:button.quiz-option
        {:class    (when result
                     (cond
                       (= opt answer)         "correct"
                       (= opt (:chosen result)) "wrong"))
         :disabled (boolean result)
         :on-click #(answer! id opt answer)}
        (str opt)])]))

;; ── Page ─────────────────────────────────────────────────────────────────────

(defn quiz-page []
  (let [{:keys [grade id]} (get-in @app-state [:route :path-params])]
    (when (or (nil? (:lesson @app-state))
              (not= (:id (:lesson @app-state)) id))
      (load-lesson! grade id))
    (fn []
      (let [{:keys [lesson loading error answers]} @app-state
            qs       (:quiz lesson)
            answered (count answers)
            total    (count qs)
            score    (->> answers vals (filter :correct?) count)
            done?    (and (pos? total) (= answered total))]
        [:div.page
         (cond
           loading [:p.loading "Loading quiz…"]
           error   [:p.error   (str "Error: " error)]
           lesson
           [:<>
            [:h1 (str "Quiz: " (:title lesson))]
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
           :else [:p.loading "Loading…"])]))))
