(ns tutor.components.math
  (:require ["katex" :as katex]))

(defn render-math
  "Render a LaTeX string inline or in display mode.
   opts: {:display? true}  for centred block equations"
  ([tex] (render-math tex {}))
  ([tex {:keys [display?]}]
   (let [html (katex/renderToString
                tex
                #js{:displayMode  (boolean display?)
                    :throwOnError false
                    :strict        false})]
     [:span {:dangerouslySetInnerHTML {:__html html}}])))

(defn math-block
  "Convenience: display-mode equation in a centred div."
  [tex]
  [:div.math-display [render-math tex {:display? true}]])
