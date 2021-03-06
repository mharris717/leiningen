(ns leiningen.trampoline
  (:refer-clojure :exclude [trampoline])
  (:use [leiningen.core :only [apply-task task-not-found abort]]
        [leiningen.compile :only [get-input-args eval-in-project
                                  get-readable-form]]
        [leiningen.classpath :only [get-classpath-string]])
  (:require [clojure.string :as string]))

(defn escape [form-string]
  (format "\"%s\"" (.replaceAll form-string "\"" "\\\\\"")))

(defn command-string [project java-cmd jvm-opts [_ form _ _ init]]
  (string/join " " [java-cmd "-cp" (get-classpath-string project)
                    "clojure.main" "-e"
                    (escape (get-readable-form nil project form init))]))

(defn write-trampoline [command]
  (spit (System/getProperty "leiningen.trampoline-file") command))

(defn trampoline
  "Calculate what needs to run in the project's process for the
provided task and run it after Leiningen's own process has exited
rather than as a subprocess of Leiningen's project.

ALPHA: subject to change without warning.

Use this to save memory or to work around things like Ant's stdin
issues. Not compatible with chaining."
  [project task-name & args]
  (let [java-cmd (format "%s/bin/java" (System/getProperty "java.home"))
        jvm-opts (get-input-args)
        jvm-opts (if (:debug project)
                   (conj jvm-opts "-Dclojure.debug=true")
                   jvm-opts)
        eval-args (atom nil)]
    (binding [eval-in-project #(do (reset! eval-args %&) 0)]
      (apply-task task-name project args task-not-found))
    (if @eval-args
      (write-trampoline (command-string project java-cmd jvm-opts @eval-args))
      (abort task-name "is not trampolineable."))))
