(ns reverie.admin.tree
  (:require [reverie.dom :as dom]
            [jayq.core :as jq]
            [jayq.util :as util]
            [crate.core :as crate]))



(defn- on-drag-start [node]
  (util/log "on-drag-start" node)
  true)


(defn- on-drag-stop [node]
  (util/log "on-drag-stop" node)
  )

(defn- on-drag-enter [node source-node]
  (util/log "on-drag-enter" node source-node)
  true)

(defn- on-drag-over [node source-node hit-mode]
  ;;(util/log "on-drag-over" node source-node hit-mode)
  "after")

(defn- on-drop [node source-node hit-mode ui draggable]
  ;;(util/log "on-drop" node source-node hit-mode ui draggable)
  (.move source-node node hit-mode ))

(defn- on-drag-leave [node source-node]
  ;;(util/log "on-drag-leave" node source-node)
  )

(defn- get-settings []
  (clj->js
   { :initAjax {:url "/admin/api/pages/read"
                :data {:mode "all"}}
    :debugLevel 0
    :imagePath "../css/dyna-skin/"
    :keyboard false
    ;;:onActivate util/log
    ;;:onDeactivate util/log
    :dnd {
          :onDragStart on-drag-start
          :onDragStop on-drag-stop
          :onDragEnter on-drag-enter
          :onDragOver on-drag-over
          :onDrop on-drop
          :onDragLeave on-drag-leave
          :autoExpandMS 1000
          :preventVoidMoves true
          }
    }))

(defn init []
  (-> (jq/$ :#tree)
      (.dynatree (get-settings))))

(defn start [$elem url]
  )



(defn ^:export dev-init []
  (util/log (get-settings))
  (-> (jq/$ :#tree)
      jq/empty)
  (init))

;;(dev-init)
