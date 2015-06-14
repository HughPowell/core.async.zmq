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

(defn- subscriber []
  (->>
   (zmq/sub-chan :connect :tcp "localhost:5556" 1)
   (async/take 10)))

(defn- publisher [control]
  (async/go
   (let [publisher (zmq/pub-chan :bind :tcp "*:5556")]
     (while (not (async/poll! control))
       (async/>! publisher (rand-int 10))))))

(deftest pub-sub []
  (let [subscriber (subscriber)
        control (async/chan)
        publisher (publisher control)]
    (async/<!! subscriber)
    (async/>!! control 1)))

(defn- sender []
  (async/go
   (->
    (zmq/push-chan :bind :tcp "*:5557")
    (async/>! greeting))))

(defn- receiver []
  (async/go
   (->
    (zmq/pull-chan :connect :tcp "localhost:5557")
    async/<!)))

(deftest push-pull []
  (let [sender (sender)
        receiver (receiver)]
    (is (= (async/<!! receiver) greeting))))
