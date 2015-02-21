(ns reverie.test.validation
  (:require [reverie.admin.validation :as v]
            [reverie.module :as module>]
            reverie.modules.auth
            [reverie.system :as sys]
            [midje.sweet :refer :all]
            vlad))


(fact
 "validate"
 (->> (v/validate
       (-> @sys/storage :modules :auth :module (module/get-entity "user"))
       {})
      (filter (fn [{:keys [selector]}]
                (some #(= selector %) [[:username] [:email]]))))
 => [{:selector [:username], :type :vlad.validations/present} {:selector [:email], :type :vlad.validations/present}])
