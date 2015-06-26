(ns ^{:skip-wiki true
      :doc "Library for creating core.async channels connected to ZeroMQ
      sockets. Each of the channels takes the following options:
      bind-or-connect: the method by which the socket connects to the system
                       topology.
                       :bind
                       :connect
      transports: the method by which this socket transmits/receives messages.
                  :inproc
                  :ipc
                  :tcp
                  :pgm
                  :epgm
      endpoint: the endpoint of the socket as a string
      options: ZeroMQ socket options
               :sndhwm"}
  core.async.zmq
  (:require [core.async.zmq.channel :as zmq])
  (:import [org.zeromq ZMQ ZMQ$Socket]))

(set! *warn-on-reflection* true)

(def ^:const version
  {:major (ZMQ/getMajorVersion)
   :minor (ZMQ/getMinorVersion)
   :patch (ZMQ/getPatchVersion)})

(defn req-chan
  "Create a ZeroMQ Request socket with a core.async channel to send and receive
  messages to and from it."
  [bind-or-connect transport endpoint & options]
  (zmq/chan ZMQ/REQ bind-or-connect transport endpoint options))

(defn rep-chan
  "Create a ZeroMQ Reply socket with a core.async channel to send and receive
  messages to and from it."
  [bind-or-connect transport endpoint & options]
  (zmq/chan ZMQ/REP bind-or-connect transport endpoint options))

(defn pub-chan
  "Create a ZeroMQ Publish socket with a core.async channel to send messages
  to it."
  [bind-or-connect transport endpoint & options]
  (-> (zmq/init-socket ZMQ/PUB bind-or-connect transport endpoint options)
      (zmq/write-only-channel zmq/serialize-data)))

(defn sub-chan
  "Create a ZeroMQ Subscribe socket with a core.async channel to receive
  messages from it."
  [bind-or-connect transport endpoint topics & options]
  (letfn [(subscribe [^ZMQ$Socket socket topic serialize-fn]
                     (letfn [(add-subscription
                              [^ZMQ$Socket socket topics]
                              (.subscribe socket ^bytes (serialize-fn topic)))]
                       (if (seq? topics)
                         (doseq [topic topics]
                           (add-subscription socket topic))
                         (add-subscription socket topic))
                       socket))]
    (-> (zmq/init-socket ZMQ/SUB bind-or-connect transport endpoint options)
        (subscribe topics zmq/serialize-topic)
        (zmq/read-only-channel zmq/deserialize))))

(defn xpub-chan
  "Create a ZeroMQ XPublish socket with a core.async channel to send messages
  to it."
  [bind-or-connect transport endpoint & options]
  (-> (zmq/init-socket ZMQ/XPUB bind-or-connect transport endpoint options)
      (zmq/write-only-channel zmq/serialize-data)))

(defn xsub-chan
  "Create a ZeroMQ XSubscribe socket with a core.async channel to receive
  messages from it."
  [bind-or-connect transport endpoint & options]
  (-> (zmq/init-socket ZMQ/XSUB bind-or-connect transport endpoint options)
      (zmq/read-only-channel zmq/deserialize)))

(defn router-chan [bind-or-connect transport endpoint & options]
  "Create a ZeroMQ Router socket with a core.async channel to send and receive
  messages to and from it."
  (zmq/chan ZMQ/ROUTER bind-or-connect transport endpoint options))

(defn dealer-chan [bind-or-connect transport endpoint & options]
  "Create a ZeroMQ Dealer socket with a core.async channel to send and receive
  messages to and from it."
  (zmq/chan ZMQ/DEALER bind-or-connect transport endpoint options))

(defn push-chan
  "Create a ZeroMQ Push socket with a core.async channel to send messages to
  it."
  [bind-or-connect transport endpoint & options]
  (-> (zmq/init-socket ZMQ/PUSH bind-or-connect transport endpoint options)
      (zmq/write-only-channel zmq/serialize-data)))

(defn pull-chan
  "Create a ZeroMQ Pull socket with a core.async channel to receive messages
  from it."
  [bind-or-connect transport endpoint & options]
  (-> (zmq/init-socket ZMQ/PULL bind-or-connect transport endpoint options)
      (zmq/read-only-channel zmq/deserialize)))

(defn pair-chan
  "Create a ZeroMQ Pair socket with a core.async channel to send and receive
  message to and from it."
  [bind-or-connect transport endpoint & options]
  (zmq/chan ZMQ/PAIR bind-or-connect transport endpoint options))

