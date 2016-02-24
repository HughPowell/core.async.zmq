(defproject core.async.zmq "0.1.0-SNAPSHOT"
  :description "Replacing the core.async channels with ZeroMQ sockets"
  :url "https://github.com/HughPowell/core.async.zmq"
  :license {:name "Mozilla Public License Version 2.0"
            :url "https://www.mozilla.org/MPL/2.0/"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.zeromq/jeromq "0.3.5"]]
;;                 [org.zeromq/jzmq "3.0.1"]]
  :repositories [["releases" {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                              :username [:gpg :env/NEXUS_USERNAME]
                              :password [:gpg :env/NEXUS_PASSWORD]}]
                 ["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots"
                               :username [:gpg :env/NEXUS_USERNAME]
                               :password [:gpg :env/NEXUS_PASSWORD]
                               :update :always}]])
