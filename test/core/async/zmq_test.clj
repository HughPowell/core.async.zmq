(ns core.async.zmq-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [core.async.zmq :as zmq]))

(deftest req-rep []
  (let [result (async/chan)]
    (async/go
     (let [client (zmq/chan :req false :tcp "localhost:5555")]
       (async/>! client "Hello")
       (is (= (async/<! client) "World"))
       (async/>! result true)))
    (async/go
     (let [server (zmq/chan :rep true :tcp "*:5555")]
       (is (= (async/<! server) "Hello"))
       (async/>! server "World")))
    (async/alts!! [result (async/timeout 10000)])))
