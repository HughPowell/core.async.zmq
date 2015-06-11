(ns core.async.zmq.hwserver
  (:require [clojure.core.async :as async]
            [core.async.zmq :as zmq]))

(defn hello-world-server
  "Hello World server
  Binds REP socket to tcp://*:5555
  Expects 'Hello' from client, replies with 'World'"
  []
  (async/go-loop
   [server (zmq/chan :rep true :tcp "*:5555")]
   ;; Wait for next request from client
   (println "Received" (async/<! server))

   ;; Do some "work"
   (async/<! (timeout 1))

   ;; Send reply back to client
   (async/>! server "World")
   (recur server)))

(defn -main []
  (hello-world-server))
