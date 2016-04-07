; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns core.async.zmq.edn-serialiser
  (:require [clojure.edn :as edn]
            [core.async.zmq.protocols :as prot]))

(def ^:const bytes-type (class (byte-array 0)))

(defmulti clj->frame class)

(defmethod clj->frame bytes-type [data]
  data)

(defmethod clj->frame nil [_]
  (byte-array 0))

(defmethod clj->frame :default [data]
  (.getBytes (pr-str data)))

(defn frame->clj [^bytes data]
  (let [non-printable-ascii (set (byte-array (range 0x00 0x1F)))]
    (if (or (some non-printable-ascii data) (nil? data))
      data
      (edn/read-string (String. data)))))

(deftype EdnSerialiser
  []
  prot/ZmqSerialiser
  (serialise-data [_ data] (clj->frame data))
  prot/ZmqDeserialiser
  (serialise-topic [_ topic] (.getBytes (str topic)))
  (deserialise [_ bytes] (frame->clj bytes)))

