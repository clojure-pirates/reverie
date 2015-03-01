(ns reverie.admin.api.editors
  (:require [clj-time.core :as t]
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [reverie.page :as page]))

(defonce edits (atom {}))
(defonce editors (atom {}))

(defn get-edit-by-user [user]
  (when user
    (->> @edits
         (filter (fn [[_ {:keys [user-id]}]]
                   (= (:id user) user-id)))
         first
         second)))

(defn edit! [page user]
  (let [edit (get-edit-by-user user)]
    (match [(cond
             (nil? edit) :free
             :else :editing)

            (cond
             (and
              (not (nil? edit))
              (= edit (get @edits (page/serial page)))) :same
             (not (nil? (get @edits (page/serial page)))) :other
             :else :free)]
           [_ :other] {:success false :error "Someone else is editing this page"}
           [_ :same] true
           [:editing _] (do (swap! edits dissoc (:serial edit))
                            (swap! edits assoc (page/serial page)
                                   {:user-id (:id user)
                                    :serial (page/serial page)
                                    :time (t/now)})
                            true)
           [:free _] (do (swap! edits assoc (page/serial page)
                                {:user-id (:id user)
                                 :serial (page/serial page)
                                 :time (t/now)})
                         true))))

(defn stop-edit! [user]
  (doseq [[k {:keys [user-id]}] @edits]
    (if (= user-id (:id user))
      (swap! edits dissoc k)))
  true)

(defn edit? [page user]
  (= (:id user) (:user-id (get @edits (page/serial page)))))


(defn editor! [user]
  {:pre [(not (nil? (:id user)))]}
  (swap! editors assoc (:id user) (t/now)))


(defn editor? [user]
  (contains? @editors (:id user)))


(defn edit-follow! [page user]
  (when-let [edit (get-edit-by-user user)]
    (swap! edits dissoc (:serial edit))
    (swap! edits assoc (page/serial page)
           {:user-id (:id user)
            :serial (page/serial page)
            :time (t/now)})))

(defn assoc-admin-css [request response]
  (if (get-in request [:reverie :edit?])
    (assoc response :body
           (str/replace (:body response)
                        #"</head>"
                        (str "<link rel='stylesheet' href='/static/admin/css/editing.css' type='text/css' />"
                             "</head>")))
    response))
