# Math Tutor

Interactive math tutor SPA — ClojureScript + Reagent + KaTeX, hosted on Cloudflare Pages.

## Stack

| Layer        | Technology           |
|--------------|----------------------|
| Language     | ClojureScript        |
| UI           | Reagent              |
| Routing      | Reitit (hash-based)  |
| Math render  | KaTeX                |
| Build        | shadow-cljs          |
| Hosting      | Cloudflare Pages     |

## Local development

```bash
# 1. Install JS deps (first time only)
npm install

# 2. Start dev server with hot reload
npm run dev
# → http://localhost:8080
```

## Production build

```bash
npm run build
# Output: public/js/main.js
```

## Project structure

```
public/
  index.html          # Single entry point
  _headers            # Cloudflare security headers
  _redirects          # SPA fallback routes
  content/
    grade1/
      addition.edn
      subtraction.edn
    grade5/
      fractions.edn

src/tutor/
  core.cljs           # Entry point, mounts Reagent root
  router.cljs         # Reitit hash router
  state.cljs          # app-state atom
  components/
    math.cljs         # KaTeX wrapper
    nav.cljs          # Navigation bar
  views/
    home.cljs         # Grade selector
    grade.cljs        # Lesson index per grade
    lesson.cljs       # Lesson renderer
    quiz.cljs         # Quiz engine
```

## Adding content

Create a new `.edn` file in `public/content/<grade>/`:

```clojure
{:id    "my-lesson"
 :grade "grade1"
 :title "My Lesson"
 :sections
 [{:type :explanation
   :text "Explanation text here."
   :math "a + b = c"}
  {:type :example
   :problem "1 + 1 = \\,?"
   :solution "1 + 1 = 2"
   :hint "Count on 1."}]
 :quiz
 [{:id "q1"
   :question "1 + 1 = \\,?"
   :options [1 2 3 4]
   :answer  2}]}
```

Then register the lesson in `src/tutor/views/grade.cljs` under `lesson-index`.

## Deploying (Cloudflare Pages — Option A)

1. Push this repo to GitHub
2. Go to [pages.cloudflare.com](https://pages.cloudflare.com)
3. Connect to Git → select this repo
4. Build settings:
   - Build command: `npm run build`
   - Output directory: `public`
   - Node version: `20`
5. Save and Deploy

Every `git push main` triggers a new deploy automatically.
