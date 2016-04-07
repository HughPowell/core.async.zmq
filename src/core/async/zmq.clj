; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns ^{:skip-wiki true
      :doc       "Library for creating core.async channels connected to ZeroMQ
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
  (:require [core.async.zmq.channel :as zmq]
            [core.async.zmq.edn-serialiser :as serialiser])
  (:import [org.zeromq ZMQ]))

(set! *warn-on-reflection* true)

(def ^:const version
  {:major (ZMQ/getMajorVersion)
   :minor (ZMQ/getMinorVersion)
   :patch (ZMQ/getPatchVersion)})

(defn req-chan
  "Create a ZeroMQ Request socket with a core.async channel to send and receive
  messages to and from it."
  ([bind-or-connect transport endpoint]
   (req-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (req-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/read-write-channel ZMQ/REQ bind-or-connect transport endpoint options serialiser)))

(defn rep-chan
  "Create a ZeroMQ Reply socket with a core.async channel to send and receive
  messages to and from it."
  ([bind-or-connect transport endpoint]
   (rep-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (rep-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/read-write-channel ZMQ/REP bind-or-connect transport endpoint options serialiser)))

(defn pub-chan
  "Create a ZeroMQ Publish socket with a core.async channel to send messages
  to it."
  ([bind-or-connect transport endpoint]
   (pub-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (pub-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/write-only-channel ZMQ/PUB bind-or-connect transport endpoint options serialiser)))

(defn sub-chan
  "Create a ZeroMQ Subscribe socket with a core.async channel to receive
  messages from it."
  ([bind-or-connect transport endpoint topics]
    (sub-chan bind-or-connect transport endpoint topics {}))
  ([bind-or-connect transport endpoint topics options]
    (sub-chan bind-or-connect transport endpoint topics options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint topics options deserialiser]
    (let [topics (if (sequential? topics) topics [topics])]
      (zmq/read-only-channel ZMQ/SUB bind-or-connect transport endpoint options deserialiser topics))))

(defn xpub-chan
  "Create a ZeroMQ XPublish socket with a core.async channel to send messages
  to it."
  ([bind-or-connect transport endpoint]
   (xpub-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (xpub-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/write-only-channel ZMQ/XPUB bind-or-connect transport endpoint options serialiser)))

(defn xsub-chan
  "Create a ZeroMQ XSubscribe socket with a core.async channel to receive
  messages from it."
  ([bind-or-connect transport endpoint]
   (xsub-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (xsub-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options deserialiser]
   (zmq/read-only-channel ZMQ/XSUB bind-or-connect transport endpoint options deserialiser)))

(defn router-chan
  "Create a ZeroMQ Router socket with a core.async channel to send and receive
  messages to and from it."
  ([bind-or-connect transport endpoint]
   (router-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (router-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/read-write-channel ZMQ/ROUTER bind-or-connect transport endpoint options serialiser)))

(defn dealer-chan
  "Create a ZeroMQ Dealer socket with a core.async channel to send and receive
  messages to and from it."
  ([bind-or-connect transport endpoint]
   (dealer-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (dealer-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/read-write-channel ZMQ/DEALER bind-or-connect transport endpoint options serialiser)))

(defn push-chan
  "Create a ZeroMQ Push socket with a core.async channel to send messages to
  it."
  ([bind-or-connect transport endpoint]
   (push-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (push-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/write-only-channel ZMQ/PUSH bind-or-connect transport endpoint options serialiser)))

(defn pull-chan
  "Create a ZeroMQ Pull socket with a core.async channel to receive messages
  from it."
  ([bind-or-connect transport endpoint]
   (pull-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (pull-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options deserialiser]
   (zmq/read-only-channel ZMQ/PULL bind-or-connect transport endpoint options deserialiser)))

(defn pair-chan
  "Create a ZeroMQ Pair socket with a core.async channel to send and receive
  message to and from it."
  ([bind-or-connect transport endpoint]
   (dealer-chan bind-or-connect transport endpoint {}))
  ([bind-or-connect transport endpoint options]
   (dealer-chan bind-or-connect transport endpoint options (serialiser/->EdnSerialiser)))
  ([bind-or-connect transport endpoint options serialiser]
   (zmq/read-write-channel ZMQ/PAIR bind-or-connect transport endpoint options serialiser)))
