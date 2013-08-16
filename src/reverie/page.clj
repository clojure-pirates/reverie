(ns reverie.page
  (:refer-clojure :exclude [get meta])
  (:require [clojure.string :as s]
            [clout.core :as clout]
            [korma.core :as k]
            [reverie.app :as app]
            [reverie.util :as util])
  (:use [reverie.atoms :exclude [objects]]
        reverie.entity))

(defn- template->str [tx]
  (if (:template tx)
    (assoc tx :template (-> tx :template util/kw->str) )
    tx))
(defn- template->kw [tx]
  (if (:template tx)
    (assoc tx :template (-> tx :template keyword))
    tx))

(defn- get-serial-page []
  (let [serial (-> page (k/select (k/aggregate (max :serial) :serial)) first :serial)]
    (if serial
      (+ 1 serial)
      1)))

(defn get* [w]
  (k/select page (k/where w)))

(defn get
  "Get a page. serial + version overrides page-id"
  [{:keys [page-id serial version] :as request}]
  (if (and serial version)
    (-> page (k/select (k/where {:serial serial :version version})) first util/revmap->kw)
    (-> page (k/select (k/where {:id page-id})) first util/revmap->kw)))

(defn- get-last-page-order [request]
  (let [p (get request)
        parent (or (:parent p) (:id p))]
    (+ 1
       (or
        (-> page (k/select (k/aggregate (max :order) :order)
                           (k/where {:parent parent})) first :order)
        -1))))

(defn objects
  "Get objects associated with a page. page-id required"
  [{:keys [page-id area version] :as request}]
  (let [version (or version (util/which-version? request))
        area (util/kw->str area)
        w {:page_id page-id :version version}
        w (cond
           (and area version) (merge w {:area area :version version})
           area (merge w {:area area})
           version (merge w {:version version})
           :else w)]
    (k/select object (k/where w))))


(defn render
  "Renders a page"
  [{:keys [uri] :as request}]
  (if-let [[route-uri page-data] (get-route uri)]
    (let [page (get (assoc request :page-id (:page-id page-data)))]
      (case (:type page-data)
        :normal (let [template (clojure.core/get @templates (-> page :template keyword))
                      f (:fn template)]
                  (f (assoc request :page-id (:id page))))
        :page (let [request (util/shorten-uri request route-uri)
                    [_ route _ f] (->> route-uri
                                       (clojure.core/get @pages)
                                       :fns
                                       (filter #(let [[method route _ _] %]
                                                  (and
                                                   (= (:request-method request) method)
                                                   (clout/route-matches route request)))))])
        (app/render (assoc request :page-data page-data :page page))))))

(defn meta [{:keys [page-id] :as request}]
  (k/select page_attributes (k/where {:page_id page-id})))

(defn add! [{:keys [tx-data] :as request}]
  (let [uri (:uri tx-data)
        type (or (:type tx-data) :normal)
        tx-data (util/revmap->str (assoc tx-data
                                     :serial (or (:serial tx-data)
                                                 (get-serial-page))
                                     :version (or (:version tx-data) 0)
                                     :updated (or (:updated tx-data) (k/sqlfn now))
                                     :type type))
        tx (k/insert page (k/values (template->str tx-data)))]
    (add-route! uri {:page-id (:id tx) :type type
                     :template (:template tx-data) :published? false})
    (assoc request :page-id (:id tx) :tx tx)))

(defn update! [{:keys [tx-data] :as request}]
  (let [p (get request)
        old-uri (:uri p)
        new-uri (:uri tx-data)
        result (k/update page (k/set-fields (util/revmap->str tx-data))
                         (k/where {:id (:id p)}))]
    (if (and
         (not (nil? new-uri))
         (not= new-uri old-uri))
      (update-route! new-uri (get-route old-uri)))
    request))

(defn delete! [{:keys [page-id] :as request}]
  (k/update page (k/set-fields {:active false :order 0}))
  request)

(defn restore! [{:keys [page-id] :as request}]
  (k/update page (k/set-fields {:active true :order (get-last-page-order request)}))
  request)

(defn publish! [{:keys [page-id] :as request}]
  (let [p (get request)
        pages (k/select page (k/where {:serial (:serial p)
                                       :active true
                                       :version [> 1]}))]
    (doseq [p pages]
      (k/update page
                (k/set-fields {:version (+ 1 (:version p))})
                (k/where {:id (:id p)})))
    (k/insert page
              (k/values (-> p
                            (dissoc :id :version)
                            (assoc :version 1))))
    (update-route! (:uri p) (assoc (get-route (:uri p)) :published? true))
    request))

(defn unpublish! [{:keys [page-id] :as request}]
  (let [p (get request)
        pages (k/select page (k/where {:serial (:serial p)
                                       :active true
                                       :version [> 0]}))]
    (doseq [p pages]
      (k/update page
                (k/set-fields {:version (+ 1 (:version p))})
                (k/where {:id (:id p)})))
    (update-route! (:uri p) (assoc (get-route (:uri p)) :published? false))
    request))