(ns reverie.core
  (:require [jayq.core :as jq]
            [jayq.util :as util]
            [reverie.admin.area :as area]
            [reverie.admin.filemanager :as filemanager]
            [reverie.admin.options :as options]
            [reverie.admin.options.object :as object]
            [reverie.admin.options.page :as page]
            [reverie.misc :as misc]
            [reverie.admin.tree :as tree]
            [reverie.dom :as dom]
            [reverie.dev :as dev]
            [reverie.meta :as meta]))



(defmulti init identity)
(defmethod init "/admin/frame/options/new-root-page" []
  (page/init))
(defmethod init "/admin/frame/options/add-page" []
  (page/init))
(defmethod init "/admin/frame/options/restore" []
  (page/init))
(defmethod init "/admin/frame/options/delete" []
  (page/init))
(defmethod init "/admin/frame/object/edit" []
  (object/init))
(defmethod init "/admin/frame/filemanager/images" []
  (filemanager/init))
(defmethod init :default []
  (meta/listen!)
  (tree/listen!)
  (area/init)
  (misc/listen!)
  (dom/$m-loaded #(dom/$m-ready area/init))
  (meta/read! (fn []
                (if (:init-root-page? @meta/data)
                  (options/new-root-page!))
                (tree/init)
                (dev/start-repl))))

(jq/document-ready
 (fn []
   (init (-> js/window .-location .-pathname))))
