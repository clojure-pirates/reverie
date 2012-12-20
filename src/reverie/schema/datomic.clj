(ns reverie.schema.datomic
  (:use [datomic.api :only [q db] :as d]
        [reverie.core :only [reverie-object]]))


;; defaults are either values or functions that return a value
(defrecord SchemaDatomic [object attributes])

(defn- expand-schema [schema]
  {:object (:object schema)
   :attributes (:attributes schema)
   :ks (keys (:attributes schema))})

(defn- migrated? [ks migrations]
  (loop [[migration & migrations] (vec migrations)
         migrated? false]
    (if (nil? migration)
      migrated?
      (let [[mks] migration]
        (if (= mks ks)
          (recur migrations true)
          (recur migrations migrated?))))))

(defn- get-migrations [connection object]
  (d/q '[:find ?ks ?object :in $ ?object :where
         [?c :reverie.object.migrations/name ?object]
         [?c :reverie.object.migrations/keys ?ks]]
       (db connection) object))

(defn- get-initials [schema]
  (let [{:keys [attributes ks]} (expand-schema schema)]
    (into {}  (map (fn [k] [k (-> (attributes k) :initial)]) ks))))

(defn- get-idents [schema]
  (let [{:keys [attributes ks]} (expand-schema schema)]
    (map (fn [k] [k (-> (attributes k) :schema :db/ident)]) ks)))

(defn- cross-initials-idents [initials idents]
  (map (fn [[k attr]] {attr (initials k)})
       idents))

(extend-type SchemaDatomic
  reverie-object
  (object-correct? [schema]
    (let [{:keys [attributes ks]} (expand-schema schema)]
      (loop [[k & ks] ks
             correct? true]
        (if (nil? k)
          correct?
          (let [values (map #(get (attributes k) %) [:schema :initial :input])]
            (if (not-any? nil? values)
              (recur ks correct?)
              (recur ks false)))))))
  (object-upgrade? [schema connection]
    (let [{:keys [object ks]} (expand-schema schema)]
      (not (migrated? ks (get-migrations connection object)))))
  (object-upgrade [schema connection]
    (let [{:keys [object attributes ks]} (expand-schema schema)
          datomic-schema (vec (map :schema (map #(attributes %) ks)))
          migrations (get-migrations connection object)]
      @(d/transact connection [{:reverie.object.migrations/name object :db/id #db/id [:db.part/user -1]}
                               {:reverie.object.migrations/keys ks :db/id #db/id [:db.part/user -1]}])
      @(d/transact connection datomic-schema)))
  (object-synchronize [schema connection]
    (let [objects (map #(let [entity (d/entity (db connection) (first %))
                              ks (keys entity)]
                          [entity ks]) (d/q '[:find ?c :in $ :where [?c :reverie/object ?o]] (db connection)))
          attribs (cross-initials-idents (get-initials schema) (get-idents schema))]
      ;;
      ))
  (object-initiate [schema connection]
    (let [{:keys [object]} (expand-schema schema)
          initials (get-initials schema)
          idents (get-idents schema)
          tmpid {:db/id #db/id [:db.part/user -1]}
          attribs (apply conj [(merge tmpid {:reverie/object object})]
                        (into []
                              (map #(merge tmpid %)
                                   (cross-initials-idents initials idents))))
          tx @(d/transact connection attribs)]
      (assoc tx :db/id (-> tx :tempids vals last))))
  (object-get [schema connection id]
    (let [{:keys [attributes ks]} (expand-schema schema)
          idents (get-idents schema)]
      true
      ))
  (object-set [schema connection data id]
    (let [idents (get-idents schema)
          attribs (map (fn [[k attr]] {attr (data k)}) idents)]
      (let [attribs (into [] (map #(merge {:db/id id} %) attribs))]
        (-> @(d/transact connection attribs) (assoc :db/id id))))))
