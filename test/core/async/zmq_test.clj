(ns core.async.zmq-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [core.async.zmq :as zmq]))

(def ^:private greeting "Hello")
(def ^:private response "World")

(defn- client []
  (async/go
   (let [client (zmq/chan :req :connect :tcp "localhost:5555")]
     (async/>! client greeting)
     (async/<! client))))

(defn- server []
  (async/go
   (let [server (zmq/chan :rep :bind :tcp "*:5555")
         result (async/<! server)]
     (async/>! server response)
     result)))


(deftest req-rep []
  (let [client (client)
        server (server)]
    (is (= (async/<!! server) greeting))
    (is (= (async/<!! client) response))))
