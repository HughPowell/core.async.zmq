(ns core.async.zmq-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [core.async.zmq :as zmq]))

(defn- client [greeting endpoint]
  (async/go
   (let [client (zmq/req-chan :connect :tcp endpoint)]
     (async/>! client greeting)
     (async/<! client))))

(defn- server [response endpoint]
  (async/go
   (let [server (zmq/rep-chan :bind :tcp endpoint)
         data (async/<! server)
         result (if (seq? data) (doall data) data)]
     (async/>! server response)
     result)))

(deftest req-rep []
  (let [greeting "Hello"
        response "World"
        client (client greeting "localhost:5555")
        server (server response "*:5555")]
    (is (= (async/<!! server) greeting))
    (is (= (async/<!! client) response))))

(defn- subscriber []
  (->>
   (zmq/sub-chan :connect :tcp "localhost:5556" 1)
   (async/take 10)))

(defn- publisher [control]
  (async/go-loop
   [publisher (zmq/pub-chan :bind :tcp "*:5556")]
   (async/>! publisher (rand-int 10))
   (when-not (async/poll! control)
     (recur publisher))))

(deftest pub-sub []
  (let [subscriber (subscriber)
        control (async/chan)
        publisher (publisher control)]
    (async/<!! subscriber)
    (async/>!! control 1)))

(defn- sender [greeting]
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
  (let [greeting "Hello"
        sender (sender greeting)
        receiver (receiver)]
    (is (= (async/<!! receiver) greeting))))

(deftest multi-part-messages []
  (let [greeting '("One" "Two" "Three")
        response [1 2 3]
        client (client greeting "localhost:5558")
        server (server response "*:5558")]
    (is (= (async/<!! server) greeting))
    (is (= (async/<!! client) response))))


(deftest router []
  (let [greeting "Hello"
        client (client greeting "localhost:5559")
        receiver (zmq/router-chan :bind :tcp "*:5559")
        response (vec (async/<!! receiver))]
    (is (= (nth response 2) greeting))
    (async/>!! receiver (assoc response 2 "World"))
    (is (= (async/<!! client) "World"))))

(deftest alt []
  (async/go-loop
   [frontend (zmq/router-chan :bind :tcp "*:5560")
    backend (zmq/dealer-chan :bind :tcp "*:5561")]
     (async/alt!
      frontend ([msg] (async/>! backend msg))
      backend ([msg] (async/>! frontend msg)))
     (recur frontend backend))

  (dotimes [n 3]
    (async/go-loop
     [worker (zmq/rep-chan :connect :tcp "localhost:5561")]
       (async/>! worker (async/<! worker))
       (recur worker)))

  (dorun
   (map
    #(async/<!! %)
    (repeatedly 3
                #(async/go-loop
                  [client (zmq/req-chan :connect :tcp "localhost:5560")
                   counter 0]
                  (when (< counter 10)
                    (async/>! client "DATA")
                    (async/<! client)
                    (recur client (inc counter))))))))
