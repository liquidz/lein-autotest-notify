(ns leiningen.autotest
  (:use
    watchtower.core)
  (:require
    [clojure.string :as str]
    [clj-growl.core :as growl]
    [leiningen.core :as lc]
    [leiningen.test :as lt])
  (:import [java.io StringWriter]))

(def ^:dynamic *growl-password* "")

(def growl
  (growl/make-growler
    *growl-password*
    "AutoTestNotify"
    ["Success" true, "Failure" true]))

(defn pickup-errors [s]
  (filter #(or (zero? (.indexOf % "FAIL"))
               (zero? (.indexOf % "ERROR")))
          (str/split s #"\n\n")))

(defn string-writer->error-message [str-writer]
  (str/join "\n\n"
    (map #(let [ss (str/split % #"\n")]
            (str/join "\n" (take 3 ss)))
         (pickup-errors (str str-writer)))))

(defn run-test [project & files]
  (let [sw (StringWriter.)]
    (binding [*out* sw]
      (if (zero? (lt/test project))
        (growl "Success" (:name project) "OK")
        (growl "Failure" (:name project) (string-writer->error-message sw))))))

(defn autotest [project & args]
  (binding [lt/*exit-after-tests* false
            lc/*interactive?*     true]
    (let [w (-> [(:root project)]
                (watcher*)
                (rate 50)
                (change-first? false)
                (file-filter ignore-dotfiles)
                (file-filter (extensions :clj :cljs))
                (on-change (fn [files] (run-test project files))))]
      (println "start watching")
      (watch w))))
