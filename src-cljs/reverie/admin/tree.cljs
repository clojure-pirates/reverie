(ns reverie.admin.tree
  (:require [reverie.dom :as dom]
            [reverie.meta :as meta]
            [reverie.admin.area :as area]
            [reverie.admin.options :as options]
            [jayq.core :as jq]
            [jayq.util :as util]
            [crate.core :as crate]))


(defn- on-drag-start [node]
  (if (false? (-> node .-data .-draggable))
    false
    true))

(defn- on-drag-enter [node source-node]
  true)

(defn- on-drag-over [node source-node hit-mode]
  (if (false? (-> node .-data .-draggable))
    false
    (clj->js ["before" "after"])))

(defn- on-drop [node source-node hit-mode ui draggable]
  (let [source-node-serial (-> source-node .-data .-serial)
        node-serial (-> node .-data .-serial)]
    (jq/xhr [:get (str "/admin/api/pages/move/"
                       node-serial
                       "/"
                       source-node-serial
                       "/"
                       hit-mode)] (clj->js {})
                       (fn [data]
                         (if (.-result data)
                           (do
                             (.expand source-node true)
                             (.move source-node node hit-mode)))))))

(defn- get-active-node []
  (-> :#tree jq/$ (.dynatree "getActiveNode")))

(defn- get-node [serial]
  (-> :#tree jq/$ (.dynatree "getTree") (.getNodeByKey (str serial))))

(defn edit-mode! [e]
  (let [serial (-> (get-active-node) .-data .-serial)]
    (jq/xhr [:get (str "/admin/api/pages/edit/" serial)]
            nil
            (fn [data]
              (if (.-result data)
                (do
                  (-> :.icon-edit-sign jq/$ (jq/add-class "hidden"))
                  (-> :.icon-eye-open jq/$ (jq/remove-class "hidden"))
                  (dom/reload-main!)))))))

(defn view-mode! [e]
  (jq/xhr [:get "/admin/api/pages/view"]
          nil
          (fn [data]
            (if (.-result data)
              (do
                (-> :.icon-eye-open jq/$ (jq/add-class "hidden"))
                (-> :.icon-edit-sign jq/$ (jq/remove-class "hidden"))
                (dom/reload-main!))))))


(defn listen! []
  (-> :.icons
      jq/$
      (jq/off :click :.icon-refresh)
      (jq/off :click :.icon-plus-sign)
      (jq/off :click :.icon-edit-sign)
      (jq/off :click :.icon-eye-open)
      (jq/off :click :.icon-trash))
  (-> :.icons
      jq/$
      (jq/on :click :.icon-refresh nil #(if-let [node (get-active-node)]
                                          (if-let [uri (-> node .-data .-uri)]
                                            (options/refresh! uri))))
      (jq/on :click :.icon-plus-sign nil #(if-let [node (get-active-node)]
                                            (if-let [serial (-> node .-data .-serial)]
                                              (options/add-page! serial))))
      (jq/on :click :.icon-edit-sign nil edit-mode!)
      (jq/on :click :.icon-eye-open nil view-mode!)
      (jq/on :click :.icon-trash nil #(if-let [node (get-active-node)]
                                            (if-let [serial (-> node .-data .-serial)]
                                              (options/delete! serial))))))

(defn on-lazy-read [node]
  (let [serial (-> node .-data .-serial)]
   (.appendAjax node (clj->js {:url (str "/admin/api/pages/read/" serial)}))))

(defn on-activation [e]
  (let [data (js->clj (.-data e) :keywordize-keys true)]
    (meta/display data)))

(defn- get-settings []
  (clj->js
   { :initAjax {:url "/admin/api/pages/read"
                :data {:mode "all"}}
    :debugLevel 0
    :imagePath "../css/dyna-skin/"
    :keyboard false
    :onLazyRead on-lazy-read
    :onActivate on-activation
    :dnd {
          :onDragStart on-drag-start
          :onDragEnter on-drag-enter
          :onDragOver on-drag-over
          :onDrop on-drop
          :autoExpandMS 1000
          :preventVoidMoves true
          }
    }))

(defn ^:export reload []
  (-> :#tree
      jq/$
      (.dynatree "getTree")
      (.reload)))

(defn ^:export added [data]
  (.addChild (get-active-node) data))

(defn ^:export deleted [data]
  (let [serial (.-serial data)
        node (get-node serial)
        parent (get-node "trash")]
    (set! (-> node .-data .-version) -1)
    (.move node parent "child")))

(defn ^:export restored [data]
  (let [node (get-node (.-serial data))
        parent (get-node (.-parent data))]
    (set! (-> node .-data .-version) 0)
    (.move node parent "child")))

(defn ^:export metad [data]
  (dom/main-uri! (.-uri data))
  (dom/show-main))

(defn ^:export init []
  ;;(util/log (get-settings))
  (-> :#tree
      jq/$
      jq/empty
      (.dynatree (get-settings))))
