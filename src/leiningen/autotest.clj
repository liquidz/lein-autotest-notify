(ns leiningen.autotest
  (:use
    watchtower.core)
  (:require
    [clojure.java.shell :as shell]
    [clojure.string     :as str]
    [clj-growl.core     :as growl]
    [leiningen.core     :as lc]
    [leiningen.test     :as lt])
  (:import [java.io StringWriter]))

(def ^:dynamic *growl-password* "")

(def linux? (= "Linux" (System/getProperty "os.name")))

(def growl-conn
  (growl/make-growler
    *growl-password* "AutoTestNotify" ["Result" true]))

(defn pickup-errors [s]
  (filter #(or (zero? (.indexOf % "FAIL"))
               (zero? (.indexOf % "ERROR"))
               (zero? (.indexOf % "Exception")))
          (str/split s #"\n\n")))

(defn string-writer->error-message [str-writer]
  (str/join "\n----\n"
    (map #(let [ss (str/split % #"\n")]
            (str/join "\n" (take 3 ss)))
         (pickup-errors (str str-writer)))))

;; notifier
(defn notify-send [title body] (shell/sh "notify-send" title body))
(defn growl [title body] (growl-conn "Result" title body))

(defn run-test [project & files]
  (let [out-sw    (StringWriter.)
        err-sw    (StringWriter.)
        proj-name (:name project)
        notify-fn (if linux? notify-send growl)]
    (notify-fn proj-name "Start testing")
    (binding [*out* out-sw, *err* err-sw]
      (let [result-code (lt/test project)
            message     (string-writer->error-message out-sw)
            message     (if (str/blank? message) "NG" message)]
        (notify-fn proj-name (if (zero? result-code) "OK" message))))))

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
