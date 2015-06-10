(ns ^{:skip-wiki true}
  core.async.zmq
  (:require [clojure.core.async.impl.protocols :as impl])
  (:import [org.zeromq ZContext ZMQ ZMQ$Socket]))

(set! *warn-on-reflection* true)

(def ^:const transport-types
  {:inproc "inproc://"
   :ipc "ipc://"
   :tcp "tcp://"
   :pgm "pgm://"
   :epgm "epgm://"})

(def ^:const socket-types
  {:pair   ZMQ/PAIR
   :pub    ZMQ/PUB
   :sub    ZMQ/SUB
   :req    ZMQ/REQ
   :rep    ZMQ/REP
   :xreq   ZMQ/XREQ
   :xrep   ZMQ/XREP
   :dealer ZMQ/DEALER
   :router ZMQ/ROUTER
   :xpub   ZMQ/XPUB
   :xsub   ZMQ/XSUB
   :pull   ZMQ/PULL
   :push   ZMQ/PUSH})

(def ^:const version
  {:major (ZMQ/getMajorVersion)
   :minor (ZMQ/getMinorVersion)
   :patch (ZMQ/getPatchVersion)})

(def ^:private ^ZContext context (ZContext.))

(defn- box [val]
  (reify clojure.lang.IDeref
    (deref [_] val)))

(deftype Channel
  [^ZMQ$Socket socket closed]
  impl/ReadPort
  (take!
   [_ handler]
   (when-not @closed
     (when-let [value (if (impl/blockable? handler)
                        (read-string (.recvStr socket))
                        (read-string (.recvStr socket ZMQ/NOBLOCK)))]
       (box value))))
  impl/WritePort
  (put!
   [_ message _]
   (if @closed
     (box false)
     (do
       (.send socket (if (string? message)
                       (str "\"" message "\"")
                       (str message)))
       (box true))))
  impl/Channel
  (closed? [_] @closed)
  (close! [_]
          (when-not @closed
            (reset! closed true)
            ;; ### The socket will be closed when the context closes.
            ;; Explicitly closing the socket here causes the process to hang
            ;; on termination when using JeroMQ.
            ;;  (.close socket)
            )))

(defn- channel [socket]
  (Channel. socket (atom false)))

(defn chan
  [type bind? transport endpoint]
  (let [socket (.createSocket context (type socket-types))
        connection (str (transport transport-types) endpoint)]
    (if bind?
      (.bind socket connection)
      (.connect socket connection))
    (channel socket)))
