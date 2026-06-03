(ns build
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn read-edn [path]
  (edn/read-string (slurp path)))

(defn ensure-vector [x]
  (if (sequential? x) (vec x) [x]))

(defn keyword->string [k]
  (if (namespace k)
    (str (namespace k) "/" (name k))
    (name k)))

(defn json-safe [x]
  (cond
    (keyword? x)
    (keyword->string x)

    (map? x)
    (into {}
          (map (fn [[k v]]
                 [(json-safe k) (json-safe v)]))
          x)

    (set? x)
    (mapv json-safe x)

    (vector? x)
    (mapv json-safe x)

    (seq? x)
    (mapv json-safe x)

    :else
    x))

(defn write-json! [path data]
  (io/make-parents path)
  (spit path
        (json/generate-string
         (json-safe data)
         {:pretty true})))

(defn ids [coll id-key]
  (set (map id-key coll)))

(defn fail! [message data]
  (throw
   (ex-info message data)))

(defn validate-unique-ids! [label coll id-key]
  (let [all-ids (map id-key coll)]
    (when-not (= (count all-ids) (count (set all-ids)))
      (fail! (str "Duplicate IDs found in " label)
             {:label label
              :ids all-ids}))))

(defn validate-subset! [label values allowed]
  (let [missing (seq (remove allowed values))]
    (when missing
      (fail! (str "Missing references in " label)
             {:label label
              :missing missing}))))

(defn exercise-refs-from-lesson [lesson]
  (->> (:lesson/blocks lesson)
       (filter #(= (:block/type %) :practice-ref))
       (map :exercise-set/id)
       set))

(defn validate! [{:keys [domains concepts skills lessons exercise-sets]}]
  (validate-unique-ids! "domains" domains :domain/id)
  (validate-unique-ids! "concepts" concepts :concept/id)
  (validate-unique-ids! "skills" skills :skill/id)
  (validate-unique-ids! "lessons" lessons :lesson/id)
  (validate-unique-ids! "exercise-sets" exercise-sets :exercise-set/id)

  (let [domain-ids      (ids domains :domain/id)
        concept-ids     (ids concepts :concept/id)
        skill-ids       (ids skills :skill/id)
        exercise-set-ids (ids exercise-sets :exercise-set/id)]

    ;; Concept validation
    (doseq [concept concepts]
      (validate-subset!
       (str (:concept/id concept) " concept/requires")
       (:concept/requires concept)
       concept-ids)

      (when-let [domain (:concept/domain concept)]
        (validate-subset!
         (str (:concept/id concept) " concept/domain")
         #{domain}
         domain-ids)))

    ;; Skill validation
    (doseq [skill skills]
      (validate-subset!
       (str (:skill/id skill) " skill/concepts")
       (:skill/concepts skill)
       concept-ids)

      (validate-subset!
       (str (:skill/id skill) " skill/requires")
       (:skill/requires skill)
       skill-ids)

      (when-let [domain (:skill/domain skill)]
        (validate-subset!
         (str (:skill/id skill) " skill/domain")
         #{domain}
         domain-ids)))

    ;; Lesson validation
    (doseq [lesson lessons]
      (validate-subset!
       (str (:lesson/id lesson) " lesson/concepts")
       (:lesson/concepts lesson)
       concept-ids)

      (validate-subset!
       (str (:lesson/id lesson) " lesson/skills")
       (:lesson/skills lesson)
       skill-ids)

      (validate-subset!
       (str (:lesson/id lesson) " exercise refs")
       (exercise-refs-from-lesson lesson)
       exercise-set-ids))

    ;; Exercise validation
    (doseq [exercise-set exercise-sets]
      (validate-subset!
       (str (:exercise-set/id exercise-set) " exercise-set/skills")
       (:exercise-set/skills exercise-set)
       skill-ids))))

(defn read-all-edn [dir-path id-key]
  (let [dir (clojure.java.io/file dir-path)]
    (if (.exists dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (filter #(clojure.string/ends-with? (.getName %) ".edn"))
           (mapcat #(ensure-vector (read-edn (.getPath %))))
           (filter #(contains? % id-key))
           (into []))
      [])))

(defn build! []
  (let [domains
        (read-all-edn "public/content/taxonomy" :domain/id)

        concepts
        (read-all-edn "public/content/concepts" :concept/id)

        skills
        (read-all-edn "public/content/skills" :skill/id)

        lessons
        (read-all-edn "public/content/lessons" :lesson/id)

        exercise-sets
        (read-all-edn "public/content/exercises" :exercise-set/id)

        data
        {:domains domains
         :concepts concepts
         :skills skills
         :lessons lessons
         :exercise-sets exercise-sets}

        demo
        {:demo/id :demo/grade1.addition
         :demo/title "Grade 1 Addition Demo"
         :lesson (first lessons)
         :exercise-sets exercise-sets}]

    (validate! data)

    (write-json! "public/compiled/domains.json" domains)
    (write-json! "public/compiled/concepts.json" concepts)
    (write-json! "public/compiled/skills.json" skills)
    (write-json! "public/compiled/lessons.json" lessons)
    (write-json! "public/compiled/exercises.json" exercise-sets)
    (write-json! "public/compiled/grade1-addition-demo.json" demo)

    (println "Build complete.")
    (println "Generated files in public/compiled/")))

(defn -main [& _args]
  (build!))

;; Execute when run as a script
(build!)