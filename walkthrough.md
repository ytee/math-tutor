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

### The "Backend": Cloudflare (Static File Host)
For this project, there is no active backend logic. Instead, Cloudflare acts as a highly distributed file server. Think of it like a remote Read-Only Memory (ROM) chip. When a user requests a URL, Cloudflare simply serves static files (HTML, JS, JSON) over HTTP.

### The "Frontend": The Browser Engine
The browser is the operating system where your firmware runs. It provides the UI framework (the Document Object Model, or DOM) and a JavaScript engine (V8) to execute your code. Your application is a **Single Page Application (SPA)**. This means Cloudflare serves a single `index.html` file containing an empty `<div>`, and then your JavaScript takes over, continuously rewriting that `<div>` to show different pages without ever reloading the browser window.

### The Pre-Processor: The Compiler
To bridge the gap between how we *write* content and how the browser *consumes* it, we have an offline build step:
1. **Source Content**: Written in EDN (Extensible Data Notation), a Clojure data format.
2. **Compiler (`build.clj`)**: A Clojure script (running on your local machine) that validates the EDN files and converts them into optimized `.json` files.
3. **Distribution**: These JSON files are pushed to Cloudflare along with the JavaScript bundle.

---

## 3. Detailed Design: Web & Clojure Concepts

This section covers the core technologies used to write the firmware for the browser.

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
When `main.js` is loaded by the browser, it calls the `init` function.
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
