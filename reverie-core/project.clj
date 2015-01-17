(defproject reverie-core "0.1.0-SNAPSHOT"
  :description ""
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.3.1"]
                 [clout "2.0.0"]
                 [slingshot "0.12.1"]
                 [org.clojure/core.match "0.2.1"]
                 [com.taoensso/timbre "3.3.1"]
                 [com.stuartsierra/component "0.2.2"]
                 [hiccup "1.0.5"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [ring-mock "0.1.3"]
                                  [reverie-sql "0.1.0-SNAPSHOT"]
                                  [reverie-batteries "0.1.0-SNAPSHOT"]
                                  [spyscope "0.1.5"]
                                  [org.postgresql/postgresql "9.3-1102-jdbc41"]]
                   :injections [(require 'spyscope.core)
                                (require 'spyscope.repl)
                                (require '[clojure.pprint :refer [pprint]])]
                   :resource-paths ["../reverie-sql/resources"]
                   :plugins [[lein-midje "3.1.3"]]}})
