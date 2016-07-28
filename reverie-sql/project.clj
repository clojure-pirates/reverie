(defproject reverie-sql "0.8.0-alpha3"
  :description "The SQL backbone of reverie; a CMS for power users"
  :url "http://reveriecms.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [reverie-core "0.8.0-alpha3"]
                 ;; database libraries
                 [org.clojure/java.jdbc "0.4.1"]
                 [honeysql "0.6.3"]
                 [yesql "0.5.2"]
                 [ez-database "0.5.3"]
                 [ez-form "0.4.4"]
                 ;; database migrations
                 [joplin.jdbc "0.3.4"]
                 ;; connection pool
                 [hikari-cp "1.6.1"]]

  :profiles {:dev {:dependencies [[org.postgresql/postgresql "9.3-1102-jdbc41"]
                                  [midje "1.6.3"]]}})
