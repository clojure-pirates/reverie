(ns reverie.page
  (:refer-clojure :exclude [get meta])
  (:require [clojure.string :as s]
            [clout.core :as clout]
            [korma.core :as k]
            [reverie.app :as app]
            [reverie.util :as util]
            [reverie.response :as r])
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

(defn- clean-save-data [data]
  (dissoc data :id :serial))

(defn- get-serial-page []
  (let [serial (-> page (k/select (k/aggregate (max :serial) :serial)) first :serial)]
    (if serial
      (+ 1 serial)
      1)))

(defn get* [w]
  (k/select page (k/where w) (k/order :order)))

(defn get
  "Get a page. serial + version overrides page-id"
  [{:keys [page-id serial version] :as request}]
  (if (and serial version)
    (-> page (k/select (k/where {:serial serial :version version})) first util/revmap->kw)
    (-> page (k/select (k/where {:id page-id})) first util/revmap->kw)))

(defn- get-last-order [request]
  (let [parent (or (:parent request) (:id (get request)))]
    (+ 1
       (or
        (-> page (k/select (k/aggregate (max :order) :order)
                           (k/where {:parent parent})) first :order)
        -1))))

(defn objects
  "Get objects associated with a page. page-id required"
  [{:keys [reverie] :as request}]
  (let [{:keys [area page-id]} reverie
        w {:page_id page-id :area (util/kw->str area)}]
    (k/select object (k/where w) (k/order :order))))

(defn render
  "Renders a page"
  [{:keys [uri] :as request}]
  (if-let [[route-uri page-data] (get-route uri)]
    (case (:type page-data)
      :normal (let [page (get {:serial (:serial page-data)
                               :version (util/which-version? request)})
                    template-options (-> page get-template :options)
                    template (clojure.core/get @templates (-> page :template keyword))
                    f (:fn template)]
                (util/middleware-wrap
                 (util/middleware-merge template-options)
                 f (-> request
                       (assoc-in [:reverie :page-serial] (:serial page))
                       (assoc-in [:reverie :page-id] (:id page)))))
      :page (let [request (util/shorten-uri request route-uri)
                  page-options (->> route-uri (clojure.core/get @pages) :options)
                  [_ route options f] (->> route-uri
                                           (clojure.core/get @pages)
                                           :fns
                                           (filter #(let [[method route _ _] %]
                                                      (and
                                                       (= (:request-method request) method)
                                                       (clout/route-matches route request))))
                                           first)]
              (if (nil? f)
                (r/response-404)
                (if (= :get (:request-method request))
                  (util/middleware-wrap
                   (util/middleware-merge page-options options)
                   f request (clout/route-matches route request))
                  
                  (util/middleware-wrap
                   (util/middleware-merge page-options options)
                   f request (clout/route-matches route request) (:params request)))))
      :app (let [page (get {:serial (:serial page-data)
                            :version (util/which-version? request)})]
             (app/render (-> request
                             (assoc-in [:reverie :page-id] (:id page))
                             (assoc-in [:reverie :page-serial] (:serial page))
                             (assoc-in [:reverie :app] (keyword (:app page))))))
      (r/response-404))))

(defn meta [{:keys [page-id] :as request}]
  (k/select page_attributes (k/where {:page_id page-id})))

(defn add! [{:keys [tx-data] :as request}]
  (let [uri (:uri tx-data)
        type (or (:type tx-data) :normal)
        tx-data (util/revmap->str (assoc tx-data
                                    :serial (or (:serial tx-data)
                                                (get-serial-page))
                                    :order (or (:order tx-data)
                                               (get-last-order request))
                                    :version (or (:version tx-data) 0)
                                    :updated (or (:updated tx-data) (k/sqlfn now))
                                    :type type))
        tx (k/insert page (k/values (template->str tx-data)))]
    (add-route! uri {:page-id (:id tx) :type (keyword type)
                     :template (:template tx-data) :published? false})
    (assoc request :page-id (:id tx) :tx tx)))

(defn update! [{:keys [tx-data] :as request}]
  (let [p (get request)
        old-uri (:uri p)
        new-uri (:uri tx-data)
        result (k/update page
                         (k/set-fields (clean-save-data (util/revmap->str tx-data)))
                         (k/where {:id (:id p)}))]
    (if (and
         (not (nil? new-uri))
         (not= new-uri old-uri))
      (update-route! new-uri (get-route old-uri)))
    {:tx result}))

(defn delete! [{:keys [serial]}]
  (k/delete page
            (k/where {:serial serial :version [> 0]}))
  (k/update page
            (k/set-fields {:order 0 :version -1})
            (k/where {:serial serial :version 0})))

(defn restore! [{:keys [serial]}]
  (k/update page
            (k/set-fields {:order (get-last-order {:serial serial :version 0})
                           :version 0})
            (k/where {:serial serial :version -1})))

(defn publish! [request]
  (let [p (get (assoc request :version 0))
        objs-to-copy (group-by
                      #(:name %)
                      (k/select object
                                (k/where {:page_id (:id p)})
                                (k/order :id)))]
    ;; delete the published version
    (if-let [p-published (get {:serial (:serial p) :version 1})]
      (let [objs-to-delete (group-by
                            #(:name %)
                            (k/select object (k/where {:page_id (:id p-published)})))]
        (doseq [[table objs] objs-to-delete]
          (k/delete table
                    (k/where {:object_id [in (map :id objs)]})))
        (k/delete object (k/where {:page_id (:id p-published)}))
        (k/delete page
                  (k/where {:serial (:serial p) :version 1}))))
    ;; publish the edited version
    (let [p-new (k/insert page
                          (k/values (-> p
                                        util/revmap->str
                                        (dissoc :id :version)
                                        (assoc :version 1))))]
      (doseq [[table objs] objs-to-copy]
        (let [obj-data (k/select table
                                 (k/where {:object_id [in (map :id objs)]})
                                 (k/order :object_id))
              copied (reduce (fn [out obj]
                               (if (nil? obj)
                                 out
                                 (conj out (k/insert
                                            object
                                            (k/values (-> obj
                                                          (dissoc :id)
                                                          (assoc :page_id (:id p-new))))))))
                             []
                            objs)]
         (k/insert table (k/values (map
                                    (fn [t-data o-data]
                                      (-> t-data
                                          (dissoc :id)
                                          (assoc :object_id (:id o-data))))
                                    obj-data copied)))
         )))
    
    (update-route! (:uri p) (assoc (second (get-route (:uri p))) :published? true))
    request))

(defn unpublish! [request]
  (let [p (get (assoc request :version 0))
        pages (k/select page (k/where {:serial (:serial p)
                                       :active true
                                       :version [> 0]}))]
    (doseq [p pages]
      (k/update page
                (k/set-fields {:version (+ 1 (:version p))})
                (k/where {:id (:id p)})))
    (update-route! (:uri p) (assoc (get-route (:uri p)) :published? false))
    request))

(defn updated! [{:keys [page-id serial]}]
  (let [w (if serial {:serial serial :version 0} {:id page-id})
        p (k/update page
                    (k/set-fields {:updated (k/sqlfn now)})
                    (k/where w))]
    (if-let [{:keys [user]} (get-in @settings [:edits (:uri p)])]
      (edit! (:uri p) user))))


(defn move! [anchor serial hit-mode]
  (let [{:keys [parent order uri name]} (get {:serial anchor :version 0})
        node (get {:serial serial :version 0})]
    (case hit-mode
      "before" (let [siblings (k/select page (k/where {:parent parent
                                                       :version 0
                                                       :order [> order]
                                                       :serial [not= serial]}))
                     new-uri (util/join-uri (util/uri-but-last-part uri)
                                            (util/uri-last-part (:uri node)))]

                 ;; update node
                 (k/update page
                           (k/set-fields {:order order :parent parent
                                          :uri new-uri})
                           (k/where {:serial serial :version 0}))
                 ;; update anchor
                 (k/update page
                           (k/set-fields {:order (+ order 1)})
                           (k/where {:serial anchor :version 0}))
                 ;; update siblings to new position after anchor and new node
                 (doseq [s siblings]
                   (k/update page
                             (k/set-fields {:order (+ (:order s) 2)})
                             (k/where {:serial (:serial s) :version 0})))
                 true)
      "after" (let [siblings (k/select page (k/where {:parent parent
                                                      :version 0
                                                      :order [> (+ order 1)]
                                                      :serial [not= serial]}))
                    new-uri (util/join-uri (util/uri-but-last-part uri)
                                            (util/uri-last-part (:uri node)))]
                ;; update node
                (k/update page
                          (k/set-fields {:order (+ order 1) :parent parent
                                         :uri new-uri})
                          (k/where {:serial serial :version 0}))
                ;; update siblings
                (doseq [s siblings]
                  (k/update page
                            (k/set-fields {:order (+ (:order s) 2)})
                            (k/where {:serial (:serial s) :version 0})))
                true)
      "over" (let [new-uri (util/join-uri uri (util/uri-last-part (:uri node)))]
               (k/update page
                         (k/set-fields {:order (get-last-order {:parent anchor})
                                        :parent anchor
                                        :uri new-uri})
                         (k/where {:serial serial :version 0}))
               true)
      false)))
