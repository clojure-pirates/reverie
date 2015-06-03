(ns reverie.site
  (:require [com.stuartsierra.component :as component]
            [noir.session :as session]
            [reverie.admin.api.editors :as editors]
            [reverie.cache :as cache]
            [reverie.database :as db]
            [reverie.object :as object]
            [reverie.page :as page]
            [reverie.render :as render]
            [reverie.response :as response]
            [reverie.route :as route]
            [reverie.system :as sys]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [slingshot.slingshot :refer [try+]])
  (:import [reverie RenderException]))

(defonce routes (atom {}))

(defprotocol ISite
  (add-route! [site route page-data])
  (reset-routes! [site])
  (get-page [site request])
  (host-match? [site request])
  (set-system-page! [site status rendered-page]))

(defn- render-page [p]
  (fn [request]
    (render/render p request)))

(defrecord Site [host-names system cachemanager
                 system-pages settings database render-fn]
  component/Lifecycle
  (start [this]
    (reset-routes! this)
    this)
  (stop [this] this)

  ISite
  (add-route! [this route properties]
    (swap! routes assoc (:path route) [route properties]))
  (reset-routes! [this]
    (swap! routes merge
           (into
            {}
            (map (fn [[route properties]]
                   {route [(route/route [route]) (select-keys properties
                                                              [:template
                                                               :app
                                                               :type
                                                               :name
                                                               :serial])]})
                 (db/get-pages-by-route database)))))
  (get-page [this {:keys [reverie] :as request}]
    (let [uri (:uri request)]
      (let [[route properties]
            (if-let [data (get @routes uri)]
              data
              (->>
               @routes
               (filter (fn [[k [r properties]]]
                         (and (not= (:type properties) :page)
                              (re-find (re-pattern (str "^" k)) uri))))
               (sort-by first)
               reverse
               first
               second))]
        (if route
          (let [{:keys [template app type name serial]} properties
                public? (not (get-in request [:reverie :editor?]))]
            (case type
              :page (let [p (db/get-page database serial public?)]
                      (if (and p
                               (= (:path route) (-> p :route :path)))
                        (assoc p :route route)
                        nil))
              :raw (let [page-data (sys/raw-page name)]
                     (page/raw-page
                      {:route route
                       :options (:options page-data)
                       :routes (:routes page-data)
                       :database database}))
              :module (assoc (:module (sys/module name))
                        :route route
                        :database database)
              :app (let [p (db/get-page database serial public?)]
                     (if (and p
                              (= (:path route) (-> p :route :path)))
                       (assoc p :route route)
                       nil))))))))

  (host-match? [this {:keys [server-name]}]
    (if (empty? host-names)
      true
      (some #(= server-name %) host-names)))

  render/IRender
  (render [this request]
    (try+
     (if-not (host-match? this request)
       ;; no match for against the host names -> 404
       (or (get system-pages 404)
           (response/get 404))
       ;; match found, go through the complicated maze towards the end...
       (if-let [p (get-page this request)]
         ;; in the event of a page found...
         (do
           ;; update edits for the admin panel
           (editors/edit-follow! p (get-in request [:reverie :user]))
           ;; update the request with required information
           (let [request (assoc-in request [:reverie :edit?]
                                   (editors/edit? p (get-in request [:reverie :user])))
                 ;; get cache hit
                 hit (if (and (page/cache? p)
                              (= 1 (page/version p)))
                       (cache/lookup cachemanager p request))]
             ;; can we even get a response?
             (if-let [resp (or hit
                               ;; only run this if hit is nil
                               (if (or
                                    (and
                                     (page/type? p :raw)
                                     (not (:forgery? (page/options p))))
                                    (false? (:forgery? (page/options p))))
                                 ;; if the type of page is raw and
                                 ;; the forgery? option is not set to true
                                 ;; OR the forgery? option is set to false
                                 ;; then we render the page as is
                                 (render/render p request)
                                 ;; otherwise we wrap in in the anti-forgery middleware and run it
                                 ((wrap-anti-forgery (render-page p)) request)))]
               ;; final adjustments on the response before we return it
               (let [final-resp
                     (update-in
                      (editors/assoc-admin-links
                       p
                       request
                       (if (map? resp)
                         (assoc resp :body (render-fn (:body resp)))
                         {:status 200
                          ;; body is either the hit or the rendered response
                          :body (or hit
                                    (render-fn resp))
                          :headers {"Content-Type" "text/html; charset=utf-8;"}}))
                      [:headers]
                      merge
                      (-> p page/options :headers))]
                 ;; when
                 ;; the request method is a GET
                 ;; AND we can cache the page
                 ;; AND the hit is nil
                 ;; AND no session flash is present to skip it
                 ;; -> cache the page
                 (when (and (nil? hit)
                            (= (:request-method request) :get)
                            (page/cache? p)
                            (not (session/flash-get :skip-cache? false))
                            (= 1 (page/version p)))
                   (cache/cache! cachemanager p (:body final-resp) request))
                 final-resp)
               ;; in the event of being unable to give a response we return a 404
               (or (get system-pages 404)
                   (response/get 404)))))
         ;; didn't find page -> 404
         (or (get system-pages 404)
             (response/get 404))))
     ;; TODO: fix the response types from reverie.core/raise-response
     (catch [:type :ring-response] {:keys [response]}
       response)
     (catch [:type :response] {:keys [status args]}
       (or
        (response/get (get system-pages status))
        (apply response/get status args)))))
  (render [this _ _]
    (throw (RenderException. "[component request sub-component] not implemented for reverie.site/Site"))))


(defn site [data]
  (map->Site data))
