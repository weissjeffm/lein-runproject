(ns leiningen.runproject
  (:require [leiningen.core.eval :as lein-eval]
            [leiningen.core.classpath :as lein-cp]
            [leiningen.core.project :as lein-project]
            [clojure.edn :as edn]))

(defn- version-string?
  "Check if a given String represents a version number."
  [^String s]
  (or (contains? #{"RELEASE" "LATEST"} s)
      (Character/isDigit (first s))))

(def ->dep-pairs
  "From a sequence of command-line args describing dependency-version pairs,
   return a list of vector pairs. Square braces in arg strings are ignored. If
   no version is given, 'RELEASE' will be used.

  Example:
  (->dep-pairs [\"clj-time\" \"\\\"0.5.1\\\"]\"])
  ; -> ([clj-time \"0.5.1\"])

  (->dep-pairs [\"[clj-time\" \"\\\"0.5.1\\\"]\"])
  ; -> ([clj-time \"0.5.1\"])
  
  (->dep-pairs [\"clj-time\" \"conformity\"])
  ; -> ([clj-time \"RELEASE\"] [conformity \"RELEASE\"])"
  (letfn [(lazy-convert [args]
            (lazy-seq 
              (when (seq args)
                (let [[^String artifact & rst] args
                      artifact (edn/read-string artifact)]
                  (if-let [[^String v & nxt] (seq rst)]
                    (if (version-string? v)
                      (cons [artifact v] (lazy-convert nxt))
                      (cons [artifact "RELEASE"] (lazy-convert rst)))
                    (vector [artifact "RELEASE"]))))))]
    (fn [args]
      (->> args
        (map #(clojure.string/replace % #"\[|\]" ""))
        lazy-convert))))

(defn- resolve-try-deps!
  "Resolve newly-added try-dependencies, adding them to classpath."
  [project]
  ;; TODO: I don't think this resolves the full hierarchy of dependencies
  (lein-cp/resolve-dependencies :dependencies project :add-classpath? true))

(defn- add-try-deps
  "Add list of try-dependencies to project."
  [deps project]
  (update-in project [:dependencies] (comp vec concat) deps))

(defn extract-project [jarfile]
  (with-open [z (java.util.zip.ZipFile. jarfile)]
    (let [tmpfile (java.io.File/createTempFile "project" ".clj")
          entry (.getEntry z "project.clj")
          project-data (-> z
                           (.getInputStream entry)
                           (clojure.java.io/copy tmpfile))]
      (lein-project/read (.getCanonicalPath tmpfile)))))

(defn ^:no-project-needed runproject

  [project & args]
  
  (println args)
  (let [deps (if (second args)
               [(first args) (second args)]
               [(first args)])
        p (-> (lein-cp/dependency-hierarchy :dependencies {:dependencies (->dep-pairs deps)})
              keys first meta :file extract-project)
        project (or project p)]
    (println project)
    (lein-eval/eval-in-project project `(do (require '~(:main project))
                                            (apply (ns-resolve '~(:main project) '~'-main) args)))))
