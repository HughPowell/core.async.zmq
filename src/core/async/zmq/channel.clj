; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns ^{:skip-wiki true}
core.async.zmq.channel
  (:require [clojure.core.async.impl.protocols :as impl]
            [clojure.core.async.impl.dispatch :as dispatch]
            [core.async.zmq.protocols.serialiser :as serialiser])
  (:import [org.zeromq ZContext ZMQ ZMQ$Socket ZMQException]
           [java.util.concurrent.locks Lock]
           [java.lang AutoCloseable]))

(def ^:const transport-types
  {:inproc "inproc://"
   :ipc    "ipc://"
   :tcp    "tcp://"
   :pgm    "pgm://"
   :epgm   "epgm://"})

(def ^:const socket-options
  {:sndhwm #(.setSndHWM ^ZMQ$Socket %1 %2)})

(def ^:private ^ZContext context
  (let [context (ZContext.)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(.close context)))
    context))

(defn- close! [^ZMQ$Socket socket closed]
  (when-not @closed
    (reset! closed true)
    (.destroySocket context socket)))

(defn- safe-recv!
  ([^ZMQ$Socket socket closed]
   (safe-recv! socket closed 0))
  ([^ZMQ$Socket socket closed flags]
   (try
     (.recv socket flags)
     (catch ZMQException e
       (close! socket closed)))))

(defn- receive!
  ([^ZMQ$Socket socket serialiser closed]
   (receive! socket serialiser closed (safe-recv! socket closed) false))
  ([^ZMQ$Socket socket serialiser closed frame]
   (receive! socket serialiser closed frame true))
  ([^ZMQ$Socket socket serialiser closed frame first?]
   (let [data (serialiser/deserialise serialiser frame)]
     (when-not @closed
       (if (.hasReceiveMore socket)
         (cons data (lazy-seq (receive! socket serialiser closed)))
         (if first? data (list data)))))))

(defn- closeable-lock
  [^Lock lock]
  (.lock lock)
  (reify
    AutoCloseable
    (close [_] (.unlock lock))))

(defn- box [result]
  (reify clojure.lang.IDeref
    (deref [_] result)))

(def ^:private pollers (atom {}))

;;; HACK: Assuming a non-zero lock-id means we're dealing with alt(s) is not
;;;       safe
(defmulti take!
          (fn [_ _ _ handler] (impl/lock-id handler)))

;;; FIXME: Take account of the handler, saving any message that is received
;;;        while the handler in inactive
(defmethod take! 0
  [^ZMQ$Socket socket serialiser closed ^Lock handler]
  (when-not @closed
    (->> (safe-recv! socket closed)
         (receive! socket serialiser closed)
         box)))

;;; HACK: Polling is not a good idea, but the only way I could get it to work
(defmethod take! :default
  [^ZMQ$Socket socket serialiser closed ^Lock handler]
  (when-not @closed
    (with-open [^AutoCloseable _ (closeable-lock handler)]
      (when (impl/active? handler)
        (let [poll (fn []
                     (with-open [^AutoCloseable _ (closeable-lock handler)]
                       (when-let [message
                                  (and (impl/active? handler)
                                       (safe-recv! socket closed ZMQ/NOBLOCK))]
                         (let [commit (impl/commit handler)]
                           (->> message
                                (receive! socket serialiser closed)
                                commit)
                           (swap! pollers dissoc (impl/lock-id handler))))))
              lock-id (impl/lock-id handler)
              poll-set (->>
                         (get @pollers lock-id #{})
                         (cons poll)
                         (swap! pollers assoc lock-id))]
          (when (= (count (poll-set lock-id)) 1)
            (dispatch/run
              (fn []
                (while (impl/active? handler)
                  (dorun (map
                           #(%)
                           (get @pollers lock-id)))
                  (Thread/sleep 1))))))))))

(defn- send! [^ZMQ$Socket socket message serializer]
  (if-not (sequential? message)
    (.send socket ^bytes (serialiser/serialise-data serializer message))
    (let [remaining (rest message)]
      (if (empty? remaining)
        (recur socket (first message) serializer)
        (do
          (.sendMore socket ^bytes (serialiser/serialise-data serializer (first message)))
          (recur socket remaining serializer))))))

(defn- put! [^ZMQ$Socket socket closed message serializer handler]
  (if @closed
    (box false)
    (with-open [^AutoCloseable _ (closeable-lock handler)]
      (try
        (send! socket message serializer)
        (catch ZMQException _ (close! socket closed) (box false)))
      (box true))))

(defn init-socket [socket-type bind-or-connect transport endpoint options]
  (let [socket (.createSocket context socket-type)
        connection (str (transport transport-types) endpoint)]
    (when (first options)
      (letfn [(apply-socket-option [opt value] ((opt socket-options) socket value))]
        (dorun (map (fn [[opt value]] (apply-socket-option opt value)) options))))
    (case bind-or-connect
      :bind (.bind socket connection)
      :connect (.connect socket connection))
    socket))

(deftype ReadWriteChannel
  [^ZMQ$Socket socket serialiser closed]
  impl/ReadPort
  (take! [_ handler]
    (take! socket serialiser closed handler))
  impl/WritePort
  (put! [_ message handler]
    (put! socket closed message serialiser handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn read-write-channel
  [socket-type bind-or-connect transport endpoint options serialiser]
  (-> (init-socket socket-type bind-or-connect transport endpoint options)
      (ReadWriteChannel. serialiser (atom false))))

(deftype ReadOnlyChannel
  [^ZMQ$Socket socket serialiser closed]
  impl/ReadPort
  (take! [_ handler]
    (take! socket serialiser closed handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn read-only-channel
  ([socket-type bind-or-connect transport endpoint options serialiser]
   (-> (init-socket socket-type bind-or-connect transport endpoint options)
       (ReadOnlyChannel. serialiser (atom false))))
  ([socket-type bind-or-connect transport endpoint options serialiser topics]
   (letfn [(add-subscription
             [^ZMQ$Socket socket topic]
             (.subscribe socket ^bytes (serialiser/serialise-topic serialiser topic)))]
     (let [socket (init-socket socket-type bind-or-connect transport endpoint options)]
       (doall (map (partial add-subscription socket) topics))
       (ReadOnlyChannel. socket serialiser (atom false))))))

(deftype WriteOnlyChannel
  [^ZMQ$Socket socket serialiser closed]
  impl/WritePort
  (put! [_ message handler] (put! socket closed message serialiser handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn write-only-channel
  [socket-type bind-or-connect transport endpoint options serialiser]
  (-> (init-socket socket-type bind-or-connect transport endpoint options)
      (WriteOnlyChannel. serialiser (atom false))))
