(ns reverie.test.object
  (:require
   [reverie.object :as object]
   [reverie.page :as page]
   [reverie.route :as route]
   [reverie.render :as render]
   [reverie.system :as sys]
   [midje.sweet :refer :all]))


(defn get-renderer
  ([]
   (get-renderer nil))
  ([methods]
   (render/map->Renderer {:name ::renderer :options {:render-fn :hiccup} :methods methods})))

(defn get-object [methods]
  (object/map->ReverieObject {:id 1 :name :test :area :a :order 1 :page nil :route (route/map->Route {})
                              :database nil :options {:renderer ::renderer} :methods methods :properties {}}))


(defn http-any [request object properties params]
  [:div "Hi " [:span "there"] "!"])


(fact
 "Simple renderer with just a :render-fn defined"
 (let [renderer (get-renderer)
       object (get-object {:any http-any})]
   (swap! sys/storage assoc-in [:renderers ::renderer] renderer)
   (render/render object {})) => "<div>Hi <span>there</span>!</div>")




(defn http-get [request object properties params]
  {:method :get})
(defn http-post [request object properties params]
  {:method :post})
(defn renderer-get [data]
  [:div "Method used was: " (:method data)])
(defn renderer-post [data]
  (render/render ::renderer :get data))



(fact
 "Advanced renderer with methods"
 (let [renderer (get-renderer {:get renderer-get :post renderer-post})
       object (get-object {:get http-get :post http-post})]
   (swap! sys/storage assoc-in [:renderers ::renderer] renderer)
   [(render/render object {:request-method :get})
    (render/render object {:request-method :post})])
 => ["<div>Method used was: get</div>"
     "<div>Method used was: post</div>"])
