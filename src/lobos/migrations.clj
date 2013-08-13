(ns lobos.migrations
  (:refer-clojure :exclude [alter defonce drop bigint boolean char double float time])
  (:use (lobos [migration :only [defmigration]] core schema)))


(defmigration init-page
  (up [] (create (table :page
                        (integer :id :primary-key :auto-inc :not-null)
                        (integer :serial :not-null)
                        (timestamp :created (default (now)) :not-null)
                        (timestamp :updated :not-null)
                        (varchar :name 255 :not-null)
                        (varchar :title 255 :not-null)
                        (varchar :template 255 :not-null)
                        (varchar :uri 2048 :not-null)
                        (integer :parent)
                        (integer :order :not-null)
                        (integer :version :not-null)

                        (index :page_index_serial [:serial])
                        (index :page_index_uri [:uri])
                        (index :page_index_name [:name])
                        (index :page_index_parent [:parent]))))
  (down [] (drop (table :page))))

(defmigration init-page-attributes
  (up [] (create (table :page_attributes
                        (integer :id :primary-key :auto-inc :not-null)
                        (timestamp :created (default (now)) :not-null)
                        (varchar :name 255 :not-null)
                        (varchar :key 100 :not-null)
                        (varchar :value 255 :not-null)
                        (integer :page_id [:refer :page :id] :not-null))))
  (down [] (drop (table :page_attributes))))

(defmigration init-role
  (up [] (create (table :role
                        (integer :id :primary-key :auto-inc :not-null)
                        (timestamp :created (default (now)) :not-null)
                        (varchar :name 255 :not-null))))
  (down [] (drop (table :role))))

(defmigration init-role-page
  (up [] (create (table :role_page
                        (integer :role_id [:refer :role :id] :not-null)
                        (integer :page_id [:refer :page :id] :not-null))))
  (down [] (drop (table :role_page))))


(defmigration init-object
  (up [] (create (table :object
                        (integer :id :primary-key :auto-inc :not-null)
                        (integer :serial :not-null)
                        (timestamp :created (default (now)) :not-null)
                        (timestamp :updated :not-null)
                        (varchar :name 255 :not-null)
                        (varchar :area 100 :not-null)
                        (integer :version :not-null)
                        (integer :page_id [:refer :page :id] :not-null)

                        (index :object_index_serial [:serial]))))
  (down [] (drop (table :object))))


(defmigration init-app
  (up [] (create (table :app
                        (integer :id :primary-key :auto-inc :not-null)
                        (varchar :name 255 :not-null)
                        (integer :page_id [:refer :page :id] :not-null))))
  (down [] (drop (table :app))))

(defmigration init-user
  (up [] (create (table :user
                        (integer :id :primary-key :auto-inc :not-null)
                        (timestamp :created (default (now)) :not-null)
                        (varchar :first_name 255 :not-null)
                        (varchar :last_name 255 :not-null)
                        (varchar :email 255 :not-null)
                        (varchar :password 100 :not-null))))
  (down [] (drop (table :user))))

(defmigration init-group
  (up [] (create (table :group
                        (integer :id :primary-key :auto-inc :not-null)
                        (timestamp :created (default (now)) :not-null)
                        (varchar :name 255 :not-null))))
  (down [] (drop (table :group))))

(defmigration init-user-group
  (up [] (create (table :user_group
                        (integer :user_id [:refer :user :id] :not-null)
                        (integer :group_id [:refer :group :id] :not-null))
                 (index :user_group_unique [:user_id :group_id] :unique)))
  (down [] (drop (table :user_group))))

(defmigration init-role-user
  (up [] (create (table :role_user
                        (integer :role_id [:refer :role :id] :not-null)
                        (integer :user_id [:refer :user :id] :not-null))))
  (down [] (drop (table :role_user))))

(defmigration init-role-group
  (up [] (create (table :role_group
                        (integer :role_id [:refer :role :id] :not-null)
                        (integer :group_id [:refer :group :id] :not-null))))
  (down [] (drop (table :role_group))))
