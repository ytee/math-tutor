# Math Tutor: Architecture & Walkthrough

This is a living document that captures the requirements, architecture, and detailed design of the Math Tutor application. It is specifically written to bridge the gap between embedded software engineering (C, microcontrollers, state machines, bare-metal memory) and modern web technologies (Browser DOM, JavaScript, ClojureScript, React).

---

## 1. Requirements

**Goal:** Build a purely client-side, interactive math tutoring web application that can serve lessons, quizzes, and educational content.
**Constraints:**
- The application must be extremely fast and cacheable.
- There is no traditional "Backend Server" (like a Python/Node.js server querying a live SQL database) at runtime.
- The content (lessons, quizzes) is authored offline in a structured format (EDN).
- The final artifact must be hostable on standard static edge infrastructure (Cloudflare Pages/Workers).

---

## 2. Architecture (High Level)

In embedded systems, you have your hardware (MCU), firmware (your code), and peripherals. In web development, the architecture is split across the network.

### The "Backend": Cloudflare Pages (Edge Delivery)
For this project, there is no active backend logic (no SQL databases, no running Node.js servers handling API calls). Instead, Cloudflare Pages acts as a highly distributed, ultra-fast file server. 

Think of Cloudflare as a massive, globally distributed Read-Only Memory (ROM) chip.
- **Objective:** To serve the application's compiled assets (HTML, JavaScript, and JSON content) to the user's browser with the absolute minimum latency possible.
- **How it Works:** When we deploy to Cloudflare Pages, our files are copied to hundreds of "edge" servers located in cities all around the world. When a user in Tokyo requests the site, they download the files from a server in Tokyo, not a centralized server in New York. This makes the initial application boot time virtually instantaneous.
- **Setup & Configuration:** The deployment behavior is governed by the `wrangler.jsonc` file. This tells the Cloudflare CLI tool (`wrangler`) which directory contains the final build artifacts (in our case, the `public` directory) and maps the domain routing.

### The "Frontend": The Browser Engine
The browser is the operating system where your firmware runs. It provides the UI framework (the Document Object Model, or DOM) and a JavaScript engine (V8) to execute your code. Your application is a **Single Page Application (SPA)**. This means Cloudflare serves a single `index.html` file containing an empty `<div>`, and then your JavaScript takes over, continuously rewriting that `<div>` to show different pages without ever reloading the browser window.

### The Pre-Processor: The Compiler
To bridge the gap between how we *write* content and how the browser *consumes* it, we have an offline build step:
1. **Source Content**: Written in EDN (Extensible Data Notation), a Clojure data format.
2. **Compiler (`build.clj`)**: A Clojure script (running on your local machine) that validates the EDN files and converts them into optimized `.json` files.
3. **Distribution**: These JSON files are pushed to Cloudflare along with the JavaScript bundle.

---

## 3. Detailed Design: Web & Clojure Concepts

This section covers the core technologies used to write the firmware for the browser and the infrastructure serving it.

### Cloudflare Pages: Deployment Mechanics
When you push code to GitHub (or run `wrangler deploy`), a build pipeline is triggered:
1. The ClojureScript code is compiled via `shadow-cljs` into `public/js/main.js`.
2. The `build.clj` script compiles the content into JSON files in `public/compiled/`.
3. The entire `public` folder is uploaded to Cloudflare.
4. **Caching:** Because these are static files, Cloudflare aggressively caches them. If you update the content, Cloudflare invalidates the cache globally so the next user gets the fresh JSON. This architecture eliminates the need to pay for or maintain a server that just sits idle 99% of the time. It is serverless.

### ClojureScript (The Language)
ClojureScript is a functional Lisp dialect that compiles into JavaScript.
- **Immutability:** In C, a `struct` in RAM can be mutated directly by pointers. In ClojureScript, data is immutable. You never change an existing map (dictionary) or vector (array). Instead, you call functions like `assoc` (associate) which return a *new* structure with the change applied. This prevents race conditions and makes UI rendering incredibly predictable.
- **Keywords (`:key`)**: Keywords are special identifiers that evaluate to themselves. They are heavily used as dictionary keys (like Enums in C). Example: `{:loading true}` is a map where the key is the `:loading` keyword and the value is the boolean `true`.

### Reagent & React (The UI Engine)
In bare-metal embedded UI development, you calculate dirty rectangles and issue SPI commands to a screen controller. 
In the web, the screen is the **DOM** (Document Object Model). Manually updating the DOM is slow and error-prone. We use a library called **React** (via a ClojureScript wrapper called **Reagent**) to handle this.
- **The Concept:** You write **pure functions** that take your current application state and return a data structure representing what the HTML *should* look like.
- **The Loop:** When your state changes, Reagent calls your UI functions again. It takes the new HTML representation, diffs it against the old one in memory, and automatically computes the exact, minimal DOM updates required to change the screen. You never write code that says "change this text to X" or "hide this button". You simply say "if state is Y, render button, else don't."

### Application State (`tutor.state`)
Because UI functions are pure, the application needs a global source of truth. 
- **The `atom`:** An atom is a thread-safe, mutable container holding an immutable value. You can change what value the atom points to using `swap!` or `reset!`.
- **The `r/atom`:** By defining our state as a Reagent atom (`r/atom`), we create a reactive variable. Whenever `swap!` is called on an `r/atom`, Reagent intercepts the change and automatically triggers a UI re-render. It is exactly like triggering a hardware interrupt that forces a display refresh.

### Reitit (The Router / State Machine)
When you navigate to a URL like `#/lesson/grade1/addition`, the `#` (hash) ensures the browser doesn't try to fetch a new HTML file from Cloudflare. Instead, a library called `reitit` intercepts the URL change. It parses the URL, figures out which logical page you want, and updates the `app-state` to point to that new route.

---

## 4. Codebase Walkthrough

Let's trace how the code executes from boot to rendering a lesson.

### 1. Boot sequence (`src/tutor/core.cljs`)
When `main.js` is loaded by the browser from Cloudflare, it calls the `init` function.
- It initializes the router: `(router/init-router!)`.
- It tells Reagent to mount the root UI component onto the HTML DOM: `(rdom/render [app] (js/document.getElementById "app"))`.

### 2. Rendering the App (`src/tutor/core.cljs`)
The `app` component is a function that returns the outer shell of the application (the Navigation bar). Inside the shell, it calls `[current-page]`.
The `current-page` component inspects the global `app-state`. It acts as a switch statement:
- If `route` is `:home`, it renders the `[home-page]`.
- If `route` is `:lesson`, it renders the `[lesson-page]`.

### 3. Transition to Lesson (`src/tutor/views/lesson.cljs`)
When the user clicks a lesson, the router updates `app-state` to the `:lesson` route. The `current-page` switch statement updates and mounts the `lesson-page` component.
The `lesson-page` component has a side-effect: on its very first render, it triggers an asynchronous network request (`js/fetch`) to ask Cloudflare for the compiled JSON data for that specific lesson (e.g., `/compiled/grade1-addition.json`).
While it waits for the network, the UI renders the `<p class="loading">Loading...</p>` text.

### 4. Fetch Completion (`src/tutor/views/lesson.cljs`)
When Cloudflare responds with the JSON, the `js/fetch` promise resolves. 
The function `store-compiled-bundle!` is called. This function uses `swap!` to inject the downloaded JSON into the global `app-state` atom and sets `:loading false`.

### 5. Reactive Re-render
Because `app-state` is a Reagent atom, the `swap!` call acts as an interrupt. Reagent notices the state change, immediately re-executes the `lesson-page` UI function, and because `:loading` is now false and `:lesson` contains data, it generates the HTML for the actual math lesson (headers, text, math equations, quizzes) and paints it to the screen.

---

## 5. Adding New Content (Authoring Guide)

The application content is statically compiled from EDN files in `public/content/`. **Whenever you add a new entity, you must update both the `.edn` content files AND the compiler script (`compiler/build.clj`).**

### Adding a New Domain
1. Open `public/content/taxonomy/domains.edn`.
2. Add a new map to the vector (e.g., `{:domain/id :domain/geometry :domain/title "Geometry" ...}`).
3. The compiler automatically reads this file, so no changes to `build.clj` are required for domains unless you create a separate file.

### Adding a New Concept
1. Open or create the appropriate concept file in `public/content/concepts/` (e.g., `geometry.edn`).
2. Add the concept map (e.g., `{:concept/id :concept/shapes ...}`).
3. **Important:** If you created a *new* file (e.g. `geometry.edn`), you must add a `read-edn` call for it in `compiler/build.clj` inside the `build!` function and combine it with the existing `concepts` vector.

### Adding a New Skill
1. Open or create the skill file in `public/content/skills/` (e.g., `geometry.edn`).
2. Define the skill map mapping to the corresponding concepts.
3. Similar to concepts, if it's a new file, update `compiler/build.clj` to read and combine it.

### Adding a New Lesson
1. Create a new directory for the grade if it doesn't exist (e.g., `public/content/lessons/grade2/`).
2. Create the lesson EDN file (e.g., `geometry-intro.edn`). Follow the schema with `:lesson/id`, `:lesson/title`, and `:lesson/blocks`.
3. **Compiler Update:** You *must* update `compiler/build.clj` to read this new file. In the `build!` function, find the `lessons` binding and add your file to it. For example:
   ```clojure
   lessons (into []
                 (concat
                  (ensure-vector (read-edn "public/content/lessons/grade1/addition-intro.edn"))
                  (ensure-vector (read-edn "public/content/lessons/grade2/geometry-intro.edn"))))
   ```

### Adding a New Exercise Set
1. Create the exercise file in `public/content/exercises/<grade>/<topic>.edn` (e.g., `public/content/exercises/grade2/shapes-basic.edn`).
2. Define the `{:exercise-set/id ... :exercise-set/exercises [...]}` map.
3. **Compiler Update:** Just like lessons, update `compiler/build.clj` in the `exercise-sets` binding to read your new file and concatenate it to the global list of exercise sets.
4. **Linking:** Make sure your lesson references the exercise set ID in a `:practice-ref` block.

### Adding a New Grade
To support a completely new grade:
1. Create the respective directories for lessons and exercises (`public/content/lessons/<grade>/` and `public/content/exercises/<grade>/`).
2. Add your content there.
3. Update `build.clj` to parse the new files.
4. Update the frontend Home Page (`src/tutor/views/home.cljs`) to actually render a card or a link to the new grade so users can navigate to it.
---

## 6. The Markdown Ingestion Tool (PDF/MD -> EDN)

Writing strict EDN by hand can be tedious. To speed up content authoring, we have built a **Strict Markdown Parser** (`compiler/ingest.clj`). You can write your lessons, concepts, and skills in a simplified Markdown format, and the compiler will automatically convert them to the strict EDN schema.

### How to Use It
1. Write your content in a `.md` file using the Strict Markdown Schema (see below).
2. Run the ingestion script:
   ```bash
   clojure -M compiler/ingest.clj path/to/your/file.md
   ```
3. The script will parse the Markdown, automatically detect the entity type (Lesson, Concept, etc.), generate the EDN file, and place it in the correct directory (e.g., `public/content/lessons/grade1/`).
4. **Note:** Because `build.clj` has been refactored to automatically scan directories, you no longer need to manually register new files in the compiler! Just run `clojure -M compiler/build.clj` to bundle your newly ingested files.

### The Strict Markdown Schema

The Markdown file is divided into two parts separated by `---`:
1. **Frontmatter:** A `key: value` block at the top containing metadata (like IDs, titles, and links to skills/concepts).
2. **Blocks:** Separated by `# ` headers. Each block represents an atomic piece of the lesson (explain, example, practice).

#### Example: A Lesson File (`addition.md`)
```markdown
type: lesson
id: :lesson/grade1.addition.advanced
title: "Advanced Addition"
grade-level: 1
domain: :arithmetic
concepts: #{:concept/addition}
skills: #{:skill/arithmetic.add-within-20}
---
# What is carrying over?
type: :explain
When you add 8 and 4, you get 12. You carry the 1 to the tens place.

# Example 1
type: :example
prompt: "8 + 4 = ?"
expression: "8 + 4 = 12"
answer: 12

# Practice Time
type: :practice-ref
exercise-set-id: :exercise-set/grade1.addition.advanced
```

### Parsing Rules
- **Keys:** Keys before the colon (`:`) are automatically namespaced based on the `type` defined at the very top. (e.g., `title: "..."` inside a lesson becomes `:lesson/title "..."`).
- **Values:** Values are parsed using Clojure's EDN reader. This means you must use strict EDN types:
  - Strings: `"Hello"`
  - Keywords: `:concept/addition`
  - Sets: `#{:skill/one :skill/two}`
  - Numbers: `42`
- **Body Text:** Any text inside a block that does not have a `key:` prefix is automatically slurped into the `:block/body` attribute.
