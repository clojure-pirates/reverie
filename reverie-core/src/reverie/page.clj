(ns reverie.page
  (:refer-clojure :exclude [type name])
  (:require [clojure.string :as str]
            [reverie.auth :refer [with-access]]
            [reverie.database :as db]
            [reverie.object :as object]
            [reverie.route :as route]
            [reverie.render :as render]
            [reverie.response :as response]
            [reverie.system :as sys]
            [reverie.util :as util]
            [schema.core :as s]
            reverie.RenderException)
  (:import [reverie RenderException]
           [reverie.object ReverieObject]
           [reverie.route Route]))


(defprotocol IPage
  (id [page])
  (serial [page])
  (parent [page])
  (root? [page])
  (children [page database])
  (children? [page database])
  (title [page])
  (name [page])
  (order [page])
  (options [page])
  (properties [page])
  (path [page])
  (slug [page])
  (objects [page])
  (type [page])
  (version [page])
  (published? [page])
  (created [page])
  (updated [page])
  (raw [page])
  (cache? [page]))


(defn type? [page expected]
  (= (type page) expected))

(defn- handle-response [options response]
  (cond
   (map? response) response
   (nil? response) response
   :else
   (let [{:keys [headers status]} (:http options)]
     {:status (or status 200)
      :headers (merge {"Content-Type" "text/html; charset=utf-8;"}
                      headers)
      :body response})))

(s/defrecord Page [route :- Route
                   id :- s/Int
                   serial :- s/Int
                   name :- s/Str
                   title :- s/Str
                   properties :- {s/Any s/Any}
                   template :- (s/either s/Keyword s/Str {s/Keyword s/Any})
                   created :- org.joda.time.DateTime
                   updated :- org.joda.time.DateTime
                   parent :- (s/maybe s/Int)
                   version :- s/Int
                   slug :- s/Str
                   database :- s/Any
                   published-date :- org.joda.time.DateTime
                   published? :- s/Bool
                   objects :- [ReverieObject]
                   raw-data :- s/Any]
  route/IRouting
  (get-route [this] route)
  (match? [this request] (route/match? route request))

  IPage
  (id [this] id)
  (serial [this] serial)
  (version [this] version)
  (published? [this] published?)
  (parent [this] parent)
  (root? [page] (nil? parent))
  (children [this database] (db/get-children database this))
  (children? [this database] (pos? (db/get-children-count database this)))
  (title [this] title)
  (name [this] name)
  (order [this] order)
  (options [this] nil)
  (properties [this] properties)
  (slug [this] slug)
  (path [this] (:path route))
  (objects [this] (sort-by :order objects))
  (type [page] :page)
  (created [page] created)
  (updated [page] updated)
  (raw [page] raw-data)
  (cache? [page] (get-in properties [:cache :cache?]))

  render/IRender
  (render [this request]
    (handle-response
     options
     (render/render template request this)))
  (render [this _ _]
    (throw (RenderException. "[component request sub-component] not implemented for reverie.page/Page"))))


(s/defrecord RawPage [route :- Route
                      options :- {s/Any s/Any}
                      routes :- [s/Any]
                      database :- s/Any]
  route/IRouting
  (get-route [this] route)
  (match? [this request] (route/match? route request))

  IPage
  (id [this] (:path route))
  (serial [this] (:path route))
  (version [this] 1)
  (published? [this] true)
  (parent [this] nil)
  (root? [this] false)
  (children [this database] nil)
  (children? [this database] false)
  (title [this] nil)
  (name [this] nil)
  (order [this] nil)
  (options [this] options)
  (properties [this] nil)
  (slug [this] nil)
  (path [this] (:path route))
  (objects [this] nil)
  (type [page] :raw)
  (created [page] nil)
  (updated [page] nil)
  (raw [page] nil)
  (cache? [page] (get-in options [:cache :cache?]))

  render/IRender
  (render [this {:keys [request-method] :as request}]
    (let [request (merge request
                         {:shortened-uri (util/shorten-uri
                                          (:uri request) (:path route))})]
      (with-access
        (get-in request [:reverie :user])
        (:required-roles options)
        (handle-response
         options
         (if-let [page-route (first (filter #(route/match? % request) routes))]
           (let [{:keys [request method]} (route/match? page-route request)]
             (if (and request method)
               (let [renderer (sys/renderer (:renderer options))
                     resp (method request this (:params request))
                     t (sys/template (:template options))
                     out (if (and t
                                  (map? resp)
                                  (not (contains? resp :status))
                                  (not (contains? resp :body))
                                  (not (contains? resp :headers)))
                           (render/render t request (assoc this :rendered resp))
                           resp)]
                 (cond
                   ;; we have provided methods to the renderer
                   (and renderer (:methods-or-routes renderer))
                   (render/render renderer request-method {:data out
                                                           :route-name (get-in page-route [:options :name])})

                   ;; we just want to utilize the render-method
                   renderer
                   (render/render renderer out)

                   ;; we don't want to do anything, just return what we got
                   :else
                   out))))
           (response/raise 404))))))
  (render [this _ _]
    (throw (RenderException. "[component request sub-component] not implemented for reverie.page/RawPage"))))


(s/defrecord AppPage [route :- Route
                      app :- s/Str
                      app-routes :- [s/Any]
                      app-area-mappings :- s/Any
                      slug :- s/Str
                      id :- s/Int
                      serial :- s/Int
                      name :- s/Str
                      title :- s/Str
                      properties :- {s/Any s/Any}
                      options :- {s/Any s/Any}
                      template :- (s/either s/Keyword s/Str {s/Keyword s/Any})
                      created :- org.joda.time.DateTime
                      updated :- org.joda.time.DateTime
                      parent :- (s/maybe s/Int)
                      database :- s/Any
                      version :- s/Int
                      published-date :- org.joda.time.DateTime
                      published? :- s/Bool
                      objects :- [ReverieObject]
                      raw-data :- s/Any]
  route/IRouting
  (get-route [this] route)
  (match? [this request]
    (with-access
      (get-in request [:reverie :user])
      (:required-roles options)
      (let [pattern (re-pattern (str "^" (:path route)))]
        (if (re-find pattern (:uri request))
          (let [uri (:uri request)
                app-uri (str/replace uri pattern "")
                app-match (first (filter #(route/match? % (assoc request :uri app-uri)) app-routes))]
            (if app-match
              (-> app-match
                  (assoc-in [:request :uri] uri)
                  (assoc-in [:request :app-uri] app-uri))))))))

  IPage
  (id [this] id)
  (serial [this] serial)
  (version [this] version)
  (published? [this] published?)
  (parent [this] parent)
  (root? [page] (nil? parent))
  (children [this database] (db/get-children database this))
  (children? [this database] (pos? (db/get-children-count database this)))
  (title [this] title)
  (name [this] name)
  (order [this] order)
  (properties [this] properties)
  (options [this] options)
  (slug [this] slug)
  (path [this] (:path route))
  (objects [this] (sort-by :order objects))
  (type [page] :app)
  (created [page] created)
  (updated [page] updated)
  (raw [page] raw-data)
  ;; allow overriding options through properties
  (cache? [page] (->> [(get-in properties [:cache :cache?])
                       (get-in options [:cache :cache?])]
                      (remove nil?)
                      first))

  render/IRender
  (render [this request]
    (let [request (merge request
                         {:shortened-uri (util/shorten-uri
                                          (:uri request) (:path route))})]
      (handle-response
       options
       (if-let [app-route (first (filter #(route/match? % request) app-routes))]
         (let [{:keys [request method]} (route/match? app-route request)
               renderer (sys/renderer (:renderer options))
               resp (method request this properties (:params request))
               out (if (map? resp)
                     (let [t (or (:template properties) template)]
                       (render/render t request (assoc this :rendered resp)))
                     resp)]
           (cond
             ;; we have provided methods to the renderer
             (and renderer (:methods-or-routes renderer))
             (render/render renderer (:request-method request)
                            {:data out
                             :route-name (get-in app-route [:options :name])})

             ;; we just want to utilize the render-method
             renderer
             (render/render renderer out)

             ;; we don't want to do anything, just return what we got
             :else
             out))))))
  (render [this _ _]
    (throw (RenderException. "[component request sub-component] not implemented for reverie.page/Page"))))


(defn page [data]
  (if (string? (:route data))
    (map->Page (assoc data :route (route/route [(:route data)])))
    (map->Page data)))

(defn raw-page [data]
  (map->RawPage data))

(defn app-page [data]
  (if (string? (:route data))
    (map->AppPage (assoc data :route (route/route [(:route data)])))
    (map->AppPage data)))
