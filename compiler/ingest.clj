(ns ingest
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(defn parse-kv-line [line]
  (let [[k v] (str/split line #":" 2)]
    (when (and k v)
      [(keyword (str/trim k)) (edn/read-string (str/trim v))])))

(defn parse-frontmatter [text]
  (let [lines (str/split-lines (str/trim text))]
    (into {} (keep parse-kv-line lines))))

(defn parse-block [block-text]
  (let [[title-line & rest-lines] (str/split-lines (str/trim block-text))
        title (str/trim title-line)
        ;; find continuous kv lines until a blank line or non-kv line
        [kv-lines body-lines] (split-with #(re-matches #"^[a-zA-Z\-]+:.*" %) rest-lines)
        metadata (into {} (keep parse-kv-line kv-lines))
        body (str/trim (str/join "\n" body-lines))]
    
    (cond-> {:block/title title}
      true (merge (reduce-kv (fn [m k v]
                               (assoc m (keyword "block" (name k)) v))
                             {} metadata))
      (not (str/blank? body)) (assoc :block/body body))))

(defn parse-markdown [content]
  (let [[frontmatter-str blocks-str] (str/split content #"(?m)^---$" 2)
        frontmatter (parse-frontmatter frontmatter-str)
        entity-type (:type frontmatter)
        base-map (dissoc frontmatter :type)]
    
    ;; Namespace all frontmatter keys to the entity type
    ;; e.g., if type is 'lesson', :id becomes :lesson/id
    (let [namespaced-frontmatter
          (reduce-kv (fn [m k v]
                       (assoc m (keyword (name entity-type) (name k)) v))
                     {} base-map)]
      
      (if (and blocks-str (not (str/blank? blocks-str)))
        (let [raw-blocks (str/split blocks-str #"(?m)^# ")
              ;; The first split might be empty if the string starts with #
              valid-blocks (remove str/blank? raw-blocks)
              parsed-blocks (mapv parse-block valid-blocks)]
          (assoc namespaced-frontmatter
                 (keyword (name entity-type) "blocks")
                 parsed-blocks))
        namespaced-frontmatter))))

(defn generate-out-path [parsed]
  ;; Determine output path based on entity type and ID
  ;; Expected ID format: :type/grade.topic.name
  (let [entity-type (-> parsed keys first namespace)
        id-key (keyword entity-type "id")
        id-val (name (get parsed id-key))
        ;; e.g. "grade1.addition.introduction" -> ["grade1" "addition.introduction"]
        [grade filename] (str/split id-val #"\." 2)]
    (if filename
      (str "public/content/" entity-type "s/" grade "/" filename ".edn")
      (str "public/content/" entity-type "s/" grade ".edn"))))

(defn -main [file]
  (if-not file
    (println "Usage: clj -M:ingest path/to/content.md")
    (let [content (slurp file)
          parsed (parse-markdown content)
          out-path (generate-out-path parsed)
          entity-type (-> parsed keys first namespace)]
      (io/make-parents out-path)
      (spit out-path (with-out-str (clojure.pprint/pprint parsed)))
      (println "Successfully converted" file "to" out-path))))

(apply -main *command-line-args*)
