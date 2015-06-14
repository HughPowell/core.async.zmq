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

(defn- take! [^ZMQ$Socket socket closed handler deserialize-fn]
  (when-not @closed
    (when-let [value (if (impl/blockable? handler)
                       (deserialize-fn (.recv socket))
                       (deserialize-fn (.recv socket ZMQ/NOBLOCK)))]
      (box value))))

(defn- put! [^ZMQ$Socket socket closed message serialize-fn]
  (if @closed
    (box false)
    (do
      (.send socket ^bytes (serialize-fn message))
      (box true))))

(defn- close! [^ZMQ$Socket socket closed]
  (when-not @closed
    (reset! closed true)
    ;; REVIEW The socket will be closed when the context closes.
    ;; Explicitly closing the socket here causes the process to hang
    ;; on termination when using JeroMQ.
    ;;  (.close socket)
    ))

(deftype ReadWriteChannel
  [^ZMQ$Socket socket serialize-fn deserialize-fn closed]
  impl/ReadPort
  (take! [_ handler] (take! socket closed handler deserialize-fn))
  impl/WritePort
  (put! [_ message _] (put! socket closed message serialize-fn))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn- read-write-channel [socket serialize-fn deserialize-fn]
  (ReadWriteChannel. socket serialize-fn deserialize-fn (atom false)))

(deftype ReadOnlyChannel
  [^ZMQ$Socket socket deserialize-fn closed]
  impl/ReadPort
  (take! [_ handler] (take! socket closed handler deserialize-fn))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn- read-only-channel [socket deserialize-fn]
  (ReadOnlyChannel. socket deserialize-fn (atom false)))

(deftype WriteOnlyChannel
  [^ZMQ$Socket socket serialize-fn closed]
  impl/WritePort
  (put! [_ message _] (put! socket closed message serialize-fn))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn- write-only-channel [socket serialize-fn]
  (WriteOnlyChannel. socket serialize-fn (atom false)))

(defn- init-socket [socket-type bind-or-connect transport endpoint]
   (let [socket (.createSocket context (socket-type socket-types))
         connection (str (transport transport-types) endpoint)]
     (case bind-or-connect
       :bind (.bind socket connection)
       :connect (.connect socket connection))
     socket))

(defn chan
  [socket-type bind-or-connect transport endpoint]
  (-> (init-socket socket-type bind-or-connect transport endpoint)
      (read-write-channel serialize-data deserialize)))

(defn pub-chan
  [bind-or-connect transport endpoint]
  (-> (init-socket :pub bind-or-connect transport endpoint)
      (write-only-channel serialize-data)))

(defn sub-chan
  [bind-or-connect transport endpoint topics]
  (letfn [(subscribe [^ZMQ$Socket socket topic serialize-fn]
                     (letfn [(add-subscription
                              [^ZMQ$Socket socket topics]
                              (.subscribe socket ^bytes (serialize-fn topic)))]
                       (if (seq? topics)
                         (doseq [topic topics]
                           (add-subscription socket topic))
                         (add-subscription socket topic))
                       socket))]
    (-> (init-socket :sub bind-or-connect transport endpoint)
        (subscribe topics serialize-topic)
        (read-only-channel deserialize))))
