(ns ^{:skip-wiki true}
  core.async.zmq.channel
  (:require [clojure.core.async.impl.protocols :as impl]
            [clojure.core.async.impl.dispatch :as dispatch]
            [clojure.edn :as edn])
  (:import [org.zeromq ZContext ZMQ ZMQ$Socket ZMQException]
           [java.util.concurrent.locks Lock]
           [java.lang AutoCloseable]))

(def ^:const bytes-type (class (byte-array 0)))

(def ^:const transport-types
  {:inproc "inproc://"
   :ipc "ipc://"
   :tcp "tcp://"
   :pgm "pgm://"
   :epgm "epgm://"})

(def ^:const socket-options
  {:sndhwm #(.setSndHWM ^ZMQ$Socket %1 %2)})

(def ^:private ^ZContext context
  (let [context (ZContext.)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(.close context)))
    context))

(defmulti serialize-data class)

(defmethod serialize-data java.lang.String [data]
  (.getBytes (str "\"" data "\"")))

(defmethod serialize-data bytes-type [data]
  data)

(defmethod serialize-data :default [data]
  (.getBytes (str data)))

(defmulti serialize-topic class)

(defmethod serialize-topic java.lang.String [topic]
  (.getBytes (str "\"" topic)))

(defmethod serialize-topic :default [topic]
  (.getBytes (str topic)))

(defn deserialize [^bytes data]
  (let [non-printable-ascii (set (byte-array (range 0x00 0x1F)))]
    (if (or (some non-printable-ascii data) (nil? data))
      data
      (edn/read-string (String. data)))))

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
  ([^ZMQ$Socket socket deserialize-fn closed]
   (receive! socket deserialize-fn closed (safe-recv! socket closed) false))
  ([^ZMQ$Socket socket deserialize-fn closed frame]
   (receive! socket deserialize-fn closed frame true))
  ([^ZMQ$Socket socket deserialize-fn closed frame first?]
   (let [data (deserialize-fn frame)]
     (when-not @closed
       (if (.hasReceiveMore socket)
         (cons data (lazy-seq (receive! socket deserialize-fn closed)))
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
  [^ZMQ$Socket socket deserialize-fn closed ^Lock handler]
  (when-not @closed
    (->> (safe-recv! socket closed)
         (receive! socket deserialize-fn closed)
         box)))

;;; HACK: Polling is not a good idea, but the only way I could get it to work
(defmethod take! :default
  [^ZMQ$Socket socket deserialize-fn closed ^Lock handler]
  (when-not @closed
    (with-open [^AutoCloseable lock (closeable-lock handler)]
      (when (impl/active? handler)
        (let [poll (fn []
                     (with-open [^AutoCloseable lock (closeable-lock handler)]
                       (when-let [message
                                  (and (impl/active? handler)
                                       (safe-recv! socket closed ZMQ/NOBLOCK))]
                         (let [commit (impl/commit handler)]
                           (->> message
                                (receive! socket deserialize-fn closed)
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

(defn- send! [^ZMQ$Socket socket message serialize-fn]
  (if-not (sequential? message)
    (.send socket ^bytes (serialize-fn message))
    (let [remaining (rest message)]
      (if (empty? remaining)
        (recur socket (first message) serialize-fn)
        (do
          (.sendMore socket ^bytes (serialize-fn (first message)))
          (recur socket remaining serialize-fn))))))

(defn- put! [^ZMQ$Socket socket closed message serialize-fn handler]
  (if @closed
    (box false)
    (with-open [^AutoCloseable lock (closeable-lock handler)]
      (try
        (send! socket message serialize-fn)
        (catch ZMQException e (close! socket closed) (box false)))
      (box true))))

(deftype ReadWriteChannel
  [^ZMQ$Socket socket serialize-fn deserialize-fn closed]
  impl/ReadPort
  (take! [_ handler]
         (take! socket deserialize-fn closed handler))
  impl/WritePort
  (put! [_ message handler]
        (put! socket closed message serialize-fn handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn read-write-channel [socket serialize-fn deserialize-fn]
  (ReadWriteChannel. socket serialize-fn deserialize-fn (atom false)))

(deftype ReadOnlyChannel
  [^ZMQ$Socket socket deserialize-fn closed]
  impl/ReadPort
  (take! [_ handler]
         (take! socket deserialize-fn closed handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn read-only-channel [socket deserialize-fn]
  (ReadOnlyChannel. socket deserialize-fn (atom false)))

(deftype WriteOnlyChannel
  [^ZMQ$Socket socket serialize-fn closed]
  impl/WritePort
  (put! [_ message handler] (put! socket closed message serialize-fn handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn write-only-channel [socket serialize-fn]
  (WriteOnlyChannel. socket serialize-fn (atom false)))

(defn init-socket [socket-type bind-or-connect transport endpoint options]
  (let [socket (.createSocket context socket-type)
        connection (str (transport transport-types) endpoint)]
    (when (first options)
      (let [opts (apply hash-map options)
            apply-socket-option
            (fn [opt value] ((opt socket-options) socket value))]
        (dorun (map (fn [[opt value]] (apply-socket-option opt value)) opts))))
    (case bind-or-connect
      :bind (.bind socket connection)
      :connect (.connect socket connection))
    socket))

(defn chan
  [socket-type bind-or-connect transport endpoint options]
  (-> (init-socket socket-type bind-or-connect transport endpoint options)
      (read-write-channel serialize-data deserialize)))
