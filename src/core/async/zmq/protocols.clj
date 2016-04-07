; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns core.async.zmq.protocols)

(defprotocol ZmqSerialiser
  "Serialise the data to a byte array to put on a core.async.zmq channel"
  (serialise-data [this data] "Serialise the given data to a byte array"))

(defprotocol ZmqDeserialiser
  "Deserialise the data from a byte array taken from a core.async.zmq channel"
  (serialise-topic [this topic] "Serialise the topic in a manner suitable for use as a subscribe topic")
  (deserialise [this bytes] "Deserialise the given byte array"))

