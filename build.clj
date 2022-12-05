(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'com.timetraveltoaster/cloudflare-dnslink)
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn jar
  "Build the JAR"
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      bb/jar))

(defn install
  "Install the JAR locally"
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      bb/install))

(defn deploy
  "Deploy the JAR to Clojars"
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      bb/deploy))