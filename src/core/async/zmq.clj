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
  {:req    ZMQ/REQ
   :rep    ZMQ/REP
   :pub    ZMQ/PUB
   :sub    ZMQ/SUB
   :xpub   ZMQ/XPUB
   :xsub   ZMQ/XSUB
   :dealer ZMQ/DEALER
   :router ZMQ/ROUTER
   :pull   ZMQ/PULL
   :push   ZMQ/PUSH
   :pair   ZMQ/PAIR})

(def ^:const version
  {:major (ZMQ/getMajorVersion)
   :minor (ZMQ/getMinorVersion)
   :patch (ZMQ/getPatchVersion)})

(def ^:private ^ZContext context
  (let [context (ZContext.)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(.close context)))
    context))

(defn- box [result]
  (reify clojure.lang.IDeref
    (deref [_] result)))

(defmulti serialize-data class)

(defmethod serialize-data java.lang.String [data]
  (.getBytes (str "\"" data "\"")))

(defmethod serialize-data :default [data]
  (.getBytes (str data)))

(defmulti serialize-topic class)

(defmethod serialize-topic java.lang.String [topic]
  (.getBytes (str "\"" topic)))

(defmethod serialize-topic :default [topic]
  (.getBytes (str topic)))

(defn deserialize [^bytes data]
  (read-string (String. data)))

(deftype Channel
  [^ZMQ$Socket socket serialize-fn deserialize-fn closed]
  impl/ReadPort
  (take!
   [_ handler]
   (when-not @closed
     (when-let [value (if (impl/blockable? handler)
                        (deserialize-fn (.recv socket))
                        (deserialize-fn (.recv socket ZMQ/NOBLOCK)))]
       (box value))))
  impl/WritePort
  (put!
   [_ message _]
   (if @closed
     (box false)
     (do
       (.send socket ^bytes (serialize-fn message))
       (box true))))
  impl/Channel
  (closed? [_] @closed)
  (close! [_]
          (when-not @closed
            (reset! closed true)
            ;; REVIEW The socket will be closed when the context closes.
            ;; Explicitly closing the socket here causes the process to hang
            ;; on termination when using JeroMQ.
            ;;  (.close socket)
            )))

(defn- channel [socket serialize-fn deserialize-fn]
  (Channel. socket serialize-fn deserialize-fn (atom false)))

(defn- subscribe [^ZMQ$Socket socket topic serialize-fn]
  (.subscribe socket ^bytes (serialize-fn topic)))

(defn chan
  ([socket-type bind-or-connect transport endpoint]
   (chan socket-type bind-or-connect transport endpoint nil))
  ([socket-type bind-or-connect transport endpoint topics]
   (let [socket (.createSocket context (socket-type socket-types))
         connection (str (transport transport-types) endpoint)]
     (case bind-or-connect
       :bind (.bind socket connection)
       :connect (.connect socket connection))
     (when topics
       (if (seq? topics)
         (doseq [topic topics] (subscribe socket topic serialize-topic))
         (subscribe socket topics serialize-topic)))
     (channel socket serialize-data deserialize))))
