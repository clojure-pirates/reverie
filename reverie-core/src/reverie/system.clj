(ns reverie.system
  (:refer-clojure :exclude [System])
  (:require [bultitude.core :refer [namespaces-on-classpath]]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [reverie.database :as db]
            [reverie.route :as route]))

(defn load-views [& dirs]
  (doseq [f (namespaces-on-classpath :classpath (map io/file dirs))]
    (require f)))

(defn load-views-ns [& ns-syms]
  (doseq [sym ns-syms
          f (namespaces-on-classpath :prefix (name sym))]
    (require f)))

(defonce storage (atom {:raw-pages {}
                        :apps {}
                        :objects {}
                        :templates {}
                        :roles {}
                        :modules {}
                        :module-default-routes []
                        :migrations {}}))

(defn apps []
  (:apps @storage))
(defn app [key]
  (get-in @storage [:apps key]))

(defn templates []
  (:templates @storage))
(defn template [key]
  (get-in @storage [:templates key]))

(defn raw-pages []
  (:raw-pages @storage))
(defn raw-page [key]
  (get-in @storage [:raw-pages key]))

(defn roles []
  (:roles @storage))
(defn role [key]
  (get-in @storage [:roles key]))

(defn objects []
  (:objects @storage))
(defn object [key]
  (get-in @storage [:objects key]))

(defn modules []
  (:modules @storage))
(defn module [key]
  (get-in @storage [:modules key]))

(defn migrations []
  (:migrations @storage))
(defn migration [key]
  (get-in @storage [:migrations key]))

(defonce ^:private sys (atom nil))

(defn get-db []
  (:database @sys))

(defn get-site []
  (:site @sys))

(defn get-filemanager []
  (:filemanager @sys))

(defn get-cachemanager []
  (:cachemanager @sys))

(defn get-settings []
  (:settings @sys))

(defrecord ReverieData [settings database filemanager cachemanager user
                        dev? edit? editor?])

(defn get-reveriedata []
  (map->ReverieData {:settings (get-settings)
                     :database (get-db)
                     :filemanager (get-filemanager)
                     :cachemanager (get-cachemanager)}))

(defrecord ReverieSystem [database site filemanager scheduler
                          settings server logger cachemanager
                          i18n]
  component/Lifecycle
  (start [this]
    (reset! sys this)
    this)
  (stop [this]
    (reset! sys nil)
    this))


(defn get-system
  ([]
     (map->ReverieSystem {}))
  ([data]
     (map->ReverieSystem data)))
