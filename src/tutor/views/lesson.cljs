(ns tutor.views.lesson
  (:require [cljs.reader   :as reader]
            [tutor.state   :refer [app-state]]
            [tutor.router  :refer [href]]))

;; ── Data loading ─────────────────────────────────────────────────────────────

(defn- clear-lesson-state! []
  (swap! app-state assoc
         :loading       true
         :error         nil
         :lesson        nil
         :exercise-sets []
         :answers       {}
         :results       {}))

(defn- store-compiled-bundle!
  "Store a compiled JSON bundle (has namespaced keys like lesson/title,
   top-level :lesson and :exercise-sets)."
  [data]
  (swap! app-state assoc
         :lesson        (:lesson data)
         :exercise-sets (vec (:exercise-sets data))
         :loading       false))

(defn- store-legacy-lesson!
  "Store an old-format EDN lesson (flat map with :title, :sections, :quiz)."
  [data]
  (swap! app-state assoc
         :lesson        data
         :exercise-sets []
         :loading       false))

(defn- try-fetch-edn
  "Try fetching a legacy EDN file. Returns a promise that resolves to the
   parsed data or rejects if not found."
  [url]
  (-> (js/fetch url)
      (.then (fn [res]
               (if (.-ok res)
                 (.text res)
                 (throw (js/Error. "not found")))))
      (.then (fn [text] (reader/read-string text)))))

(defn load-lesson!
  "Load lesson data for the given grade and id.
   Tries the compiled JSON bundle first, then falls back to legacy EDN
   (trying both {id}.edn and {id}-intro.edn filenames)."
  [grade id]
  (clear-lesson-state!)
  (let [compiled-url (str "/compiled/" grade "-" id ".json")
        legacy-url   (str "/content/lessons/" grade "/" id ".edn")
        legacy-alt   (str "/content/lessons/" grade "/" id "-intro.edn")]
    (-> (js/fetch compiled-url)
        (.then
         (fn [res]
           (if (.-ok res)
             ;; Compiled bundle found — parse as JSON
             (-> (.json res)
                 (.then (fn [json]
                          (store-compiled-bundle!
                           (js->clj json :keywordize-keys true)))))
             ;; Compiled bundle not found — try legacy EDN
             (-> (try-fetch-edn legacy-url)
                 (.catch (fn [_] (try-fetch-edn legacy-alt)))
                 (.then  (fn [data] (store-legacy-lesson! data)))))))
        (.catch
         (fn [err]
           (swap! app-state assoc
                  :error   (str "Lesson not found: " grade "/" id)
                  :loading false))))))

;; ── Lesson block renderers (compiled format) ─────────────────────────────────

(defn- compiled-explain-block [block]
  [:div.section
   [:h2 (:block/title block)]
   [:p  (:block/body block)]])

(defn- compiled-example-block [block]
  [:div.section
   [:div.example-block
    [:h2 (:block/title block)]
    [:p  (:block/prompt block)]
    (when (:block/expression block)
      [:p [:strong (:block/expression block)]])
    (when (:block/explanation block)
      [:p.hint (:block/explanation block)])]])

(defn- compiled-lesson-block [block]
  (case (:block/type block)
    "explain"      [compiled-explain-block block]
    "example"      [compiled-example-block block]
    "practice-ref" nil
    [:div.section
     [:p.error (str "Unknown block type: " (:block/type block))]]))

;; ── Legacy section renderers (old format) ────────────────────────────────────

(defn- legacy-explanation [section]
  [:div.section
   [:p (:text section)]
   (when (:math section)
     [:p [:strong (:math section)]])])

(defn- legacy-example [section]
  [:div.section
   [:div.example-block
    [:p [:strong (:problem section)]]
    [:p (:solution section)]
    (when (:hint section)
      [:p.hint (:hint section)])]])

(defn- legacy-section [section]
  (case (name (:type section))
    "explanation" [legacy-explanation section]
    "example"     [legacy-example section]
    [:div.section
     [:p.error (str "Unknown section type: " (:type section))]]))

;; ── Exercise rendering (compiled format) ─────────────────────────────────────

(defn- parse-int-safe [s]
  (let [n (js/parseInt s 10)]
    (when-not (js/isNaN n) n)))

(defn- check-answer! [exercise-id correct-answer]
  (let [user-answer (get-in @app-state [:answers exercise-id])
        parsed      (parse-int-safe user-answer)
        correct?    (= parsed correct-answer)]
    (swap! app-state assoc-in [:results exercise-id] correct?)))

(defn- exercise-item [item]
  (let [exercise-id (:exercise/id item)
        current     (get-in @app-state [:answers exercise-id] "")
        result      (get-in @app-state [:results exercise-id])]
    [:div.exercise
     [:p [:strong (:exercise/prompt item)]]

     [:input
      {:type        "number"
       :value       current
       :placeholder "Your answer"
       :on-change
       (fn [e]
         (swap! app-state assoc-in
                [:answers exercise-id]
                (.. e -target -value)))}]

     [:button.btn
      {:style    {:margin-left "0.5rem"}
       :on-click #(check-answer! exercise-id (:exercise/answer item))}
      "Check"]

     (when (some? result)
       [:div.feedback
        (if result
          [:p.correct "Correct!"]
          [:p.error "Try again."])
        (when (:exercise/explanation item)
          [:p.hint (:exercise/explanation item)])])]))

(defn- exercise-set-view [exercise-set]
  [:div.section
   [:h2 (:exercise-set/title exercise-set)]
   (for [item (:exercise-set/items exercise-set)]
     ^{:key (:exercise/id item)}
     [exercise-item item])])

;; ── Page ─────────────────────────────────────────────────────────────────────

(defn- compiled-format?
  "True when the loaded lesson uses the compiled (namespaced-key) format."
  [lesson]
  (some? (:lesson/title lesson)))

(defn- lesson-content []
  (let [{:keys [lesson exercise-sets loading error]} @app-state]
    [:div.page
     (cond
       loading
       [:p.loading "Loading lesson…"]

       error
       [:p.error (str "Error: " error)]

       (and lesson (compiled-format? lesson))
       ;; ── Compiled format ──
       [:<>
        [:h1 (:lesson/title lesson)]

        (for [block (:lesson/blocks lesson)]
          ^{:key (:block/id block)}
          [compiled-lesson-block block])

        (for [es exercise-sets]
          ^{:key (:exercise-set/id es)}
          [exercise-set-view es])

        [:div {:style {:margin-top "2rem"}}
         [:a.btn.btn-outline {:href (href :home)}
          "← Back to home"]]]

       lesson
       ;; ── Legacy format ──
       [:<>
        [:h1 (:title lesson)]

        (for [[i section] (map-indexed vector (:sections lesson))]
          ^{:key i}
          [legacy-section section])

        [:div {:style {:margin-top "2rem"}}
         [:a.btn.btn-outline {:href (href :home)}
          "← Back to home"]]]

       :else
       [:p.loading "Loading…"])]))

(defn lesson-page [grade id]
  (let [loaded-key (atom nil)]
    (fn [grade id]
      (let [key (str grade "/" id)]
        (when (not= @loaded-key key)
          (reset! loaded-key key)
          (js/setTimeout #(load-lesson! grade id) 0)))
      [lesson-content])))