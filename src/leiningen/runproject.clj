(ns leiningen.runproject
  (:require [leiningen.core.eval :as lein-eval]
            [leiningen.core.classpath :as lein-cp]
            [leiningen.core.project :as lein-project]
            [clojure.edn :as edn]))

;; Following function written by https://github.com/rkneufeld
;; taken from https://github.com/rkneufeld/lein-try

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

(defn extract-project
  "Extract project.clj from jarfile, and read it as a project."
  [jarfile]
  (with-open [z (java.util.zip.ZipFile. jarfile)]
    (let [tmpfile (java.io.File/createTempFile "project" ".clj")
          entry (.getEntry z "project.clj")]
      (try (-> z
               (.getInputStream entry)
               (clojure.java.io/copy tmpfile))
           (lein-project/read (.getCanonicalPath tmpfile))
           (finally 
             (.delete tmpfile))))))

(defn ^:no-project-needed runproject
  "Launch a project's 'main' directly from the repository.
   Ignores the current directory's project if there is one.
   Usage:

    lein runproject com.foocorp/foo-tool 0.2.1 arg1 arg2 arg3 ...

    # This uses the most recent version (not including snapshots).
    lein runproject com.foocorp/foo-tool arg1 arg2 arg3 ... "
  [_ & args]
  (let [deps (->dep-pairs (if (second args)
                            [(first args) (second args)]
                            [(first args)]))
        fake-project (lein-project/make {:dependencies deps})]
    (println (lein-cp/resolve-dependencies :dependencies fake-project :add-classpath? true))
    (let [project (-> (lein-cp/dependency-hierarchy :dependencies fake-project)
                      keys first meta :file extract-project
                      (update-in [:dependencies] concat deps)
                      (dissoc :source-paths :java-source-paths :test-paths :resource-paths))]
      (if-let [main (:main project)]
        (lein-eval/eval-in-project project `(do (require '~main)
                                                (apply (ns-resolve '~main '~'-main) (quote ~args))))
        (throw (IllegalArgumentException. "Project does not specify a :main namespace to run."))))))
