(ns tutor.views.lesson
  (:require [tutor.state  :refer [app-state]]
            [tutor.router :refer [href]]))

;; ── Data loading ─────────────────────────────────────────────────────────────

(defn load-grade1-addition-demo! []
  (swap! app-state assoc
         :loading true
         :error nil
         :demo nil
         :answers {}
         :results {})

  (-> (js/fetch "/compiled/grade1-addition-demo.json")
      (.then
       (fn [res]
         (if (.-ok res)
           (.json res)
           (throw (js/Error. (str "Not found: " (.-status res)))))))
      (.then
       (fn [data]
         (swap! app-state assoc
                :demo    (js->clj data :keywordize-keys true)
                :loading false)))
      (.catch
       (fn [err]
         (swap! app-state assoc
                :error   (.-message err)
                :loading false)))))

(defn load-lesson!
  "Temporary compatibility wrapper for old quiz.cljs.
   For the Grade 1 demo, always loads the compiled addition demo JSON."
  [_grade _id]
  (load-grade1-addition-demo!))                

;; ── Lesson block renderers ───────────────────────────────────────────────────

(defn explain-block [block]
  [:div.section
   [:h2 (:block/title block)]
   [:p (:block/body block)]])

(defn example-block [block]
  [:div.section
   [:div.example-block
    [:h2 (:block/title block)]
    [:p (:block/prompt block)]
    (when (:block/expression block)
      [:p [:strong (:block/expression block)]])
    (when (:block/explanation block)
      [:p.hint (:block/explanation block)])]])

(defn lesson-block [block]
  (case (:block/type block)
    "explain"      [explain-block block]
    "example"      [example-block block]
    "practice-ref" nil
    [:div.section
     [:p.error (str "Unknown block type: " (:block/type block))]]))

;; ── Exercise rendering ───────────────────────────────────────────────────────

(defn parse-int-safe [s]
  (let [n (js/parseInt s 10)]
    (when-not (js/isNaN n)
      n)))

(defn check-answer! [exercise-id correct-answer]
  (let [user-answer (get-in @app-state [:answers exercise-id])
        parsed      (parse-int-safe user-answer)
        correct?    (= parsed correct-answer)]
    (swap! app-state assoc-in [:results exercise-id] correct?)))

(defn exercise-item [item]
  (let [exercise-id (:exercise/id item)
        current     (get-in @app-state [:answers exercise-id] "")
        result      (get-in @app-state [:results exercise-id])]
    [:div.exercise
     [:p [:strong (:exercise/prompt item)]]

     [:input
      {:type "number"
       :value current
       :placeholder "Your answer"
       :on-change
       (fn [e]
         (swap! app-state assoc-in
                [:answers exercise-id]
                (.. e -target -value)))}]

     [:button.btn
      {:style {:margin-left "0.5rem"}
       :on-click #(check-answer! exercise-id (:exercise/answer item))}
      "Check"]

     (when (some? result)
       [:div.feedback
        (if result
          [:p.correct "Correct!"]
          [:p.error "Try again."]
          )
        (when (:exercise/explanation item)
          [:p.hint (:exercise/explanation item)])])]))

(defn exercise-set-view [exercise-set]
  [:div.section
   [:h2 (:exercise-set/title exercise-set)]
   (for [item (:exercise-set/items exercise-set)]
     ^{:key (:exercise/id item)}
     [exercise-item item])])

;; ── Page ─────────────────────────────────────────────────────────────────────

(defn lesson-content []
  (let [{:keys [demo loading error]} @app-state]
    [:div.page
     (cond
       loading
       [:p.loading "Loading lesson…"]

       error
       [:p.error (str "Error: " error)]

       demo
       (let [lesson        (:lesson demo)
             exercise-sets (:exercise-sets demo)]
         [:<>
          [:h1 (:lesson/title lesson)]

          (for [block (:lesson/blocks lesson)]
            ^{:key (:block/id block)}
            [lesson-block block])

          (for [exercise-set exercise-sets]
            ^{:key (:exercise-set/id exercise-set)}
            [exercise-set-view exercise-set])

          [:div {:style {:margin-top "2rem"}}
           [:a.btn.btn-outline {:href (href :home)}
            "← Back to home"]]])

       :else
       [:p.loading "Loading…"])]))

(defn lesson-page
  ;; Supports both usages:
  ;; [lesson-page]
  ;; [lesson-page "grade1" "addition"]
  ([]
   [lesson-page nil nil])

  ([grade id]
   (let [loaded? (atom false)]
     (fn [_grade _id]
       (when-not @loaded?
         (reset! loaded? true)
         (load-grade1-addition-demo!))
       [lesson-content]))))