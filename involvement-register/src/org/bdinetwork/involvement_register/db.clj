(ns org.bdinetwork.involvement-register.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection])
  (:import com.zaxxer.hikari.HikariDataSource))

(defn datasource
  "Creates an initialized datasource.

  This is an AutoCloseable, so can be used as an
  nl.jomco.resources.Resource.

  If config only provides `dbname`, authenticates as the current
  user."
  [{:keys [dbname dbuser dbpassword] :as  _config}]
  (let [ds (connection/->pool com.zaxxer.hikari.HikariDataSource
                              {:dbtype "postgres" :dbname dbname :username dbuser :password dbpassword})]
    ;; open and close connection, making sure the provided
    ;; configuration is correct and the database is reachable.
    ;;
    ;; See also
    (try
      (.close (jdbc/get-connection ds))
      (catch Exception e
        (.close ds)
        (throw e)))
    ds))

(comment
  (def ds (datasource {:dbname "involvements"})))
