; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns core.async.zmq.edn-serialiser
  (:require [clojure.edn :as edn]
            [core.async.zmq.protocols.serialiser :as serialiser]))

(def ^:const bytes-type (class (byte-array 0)))

(defmulti serialise-data-to-edn class)

(defmethod serialise-data-to-edn java.lang.String [data]
  (.getBytes (str "\"" data "\"")))

(defmethod serialise-data-to-edn bytes-type [data]
  data)

(defmethod serialise-data-to-edn :default [data]
  (.getBytes (str data)))

(defmulti serialise-topic-to-edn class)

(defmethod serialise-topic-to-edn java.lang.String [topic]
  (.getBytes (str "\"" topic)))

(defmethod serialise-topic-to-edn :default [topic]
  (.getBytes (str topic)))

(defn deserialise-from-edn [^bytes data]
  (let [non-printable-ascii (set (byte-array (range 0x00 0x1F)))]
    (if (or (some non-printable-ascii data) (nil? data))
      data
      (edn/read-string (String. data)))))

(deftype EdnSerialiser
  []
  serialiser/ZmqSerialiser
  (serialise-data [_ data] (serialise-data-to-edn data))
  (serialise-topic [_ topic] (serialise-topic-to-edn topic))
  (deserialise [_ bytes] (deserialise-from-edn bytes)))

