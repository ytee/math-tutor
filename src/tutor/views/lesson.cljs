(ns tutor.views.lesson
  (:require [tutor.state            :refer [app-state]]
            [tutor.router           :refer [href]]
            [tutor.components.math  :refer [render-math math-block]]
            [clojure.edn            :as edn]))

;; ── Data loading ─────────────────────────────────────────────────────────────

(defn load-lesson! [grade id]
  (swap! app-state assoc :loading true :error nil)
  (-> (js/fetch (str "/content/" grade "/" id ".edn"))
      (.then (fn [res]
               (if (.-ok res)
                 (.text res)
                 (throw (js/Error. (str "Not found: " (.-status res)))))))
      (.then (fn [text]
               (swap! app-state assoc
                      :lesson  (edn/read-string text)
                      :answers {}
                      :loading false)))
      (.catch (fn [err]
                (swap! app-state assoc
                       :error   (.-message err)
                       :loading false)))))

;; ── Section renderers ─────────────────────────────────────────────────────────

(defmulti section-view :type)

(defmethod section-view :explanation [{:keys [text math]}]
  [:div.section
   [:p text]
   (when math [math-block math])])

(defmethod section-view :example [{:keys [problem solution hint]}]
  [:div.section
   [:div.example-block
    [:p [:strong "Example: "] [render-math problem]]
    [:p [:strong "Answer: "]  [render-math solution]]
    (when hint [:p.hint "Hint: " hint])]])

(defmethod section-view :default [s]
  [:div.section [:p.error (str "Unknown section type: " (:type s))]])

;; ── Page ─────────────────────────────────────────────────────────────────────

(defn lesson-page []
  (let [{:keys [grade id]} (get-in @app-state [:route :path-params])]
    ;; Load when navigated in or when a different lesson is shown
    (when (or (nil? (:lesson @app-state))
              (not= (:id (:lesson @app-state)) id))
      (load-lesson! grade id))
    (fn []
      (let [{:keys [lesson loading error]} @app-state]
        [:div.page
         (cond
           loading [:p.loading "Loading lesson…"]
           error   [:p.error   (str "Error: " error)]
           lesson
           [:<>
            [:h1 (:title lesson)]
            (for [s (:sections lesson)]
              ^{:key (str s)} [section-view s])
            [:div {:style {:margin-top "2rem" :display "flex" :gap "1rem"}}
             [:a.btn {:href (href :quiz {:grade grade :id id})}
              "Take quiz →"]
             [:a.btn.btn-outline {:href (href :grade {:grade grade})}
              "← Back"]]]
           :else [:p.loading "Loading…"])]))))
