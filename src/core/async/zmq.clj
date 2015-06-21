(ns ^{:skip-wiki true}
  core.async.zmq
  (:require [clojure.core.async.impl.protocols :as impl]
            [clojure.core.async.impl.dispatch :as dispatch]
            [clojure.core.async.impl.mutex :as mutex]
            [clojure.core.async :as async]
            [clojure.edn :as edn])
  (:import [org.zeromq ZContext ZMQ ZMQ$Socket]
           [java.util.concurrent.locks Lock]
           [java.lang AutoCloseable]))

(set! *warn-on-reflection* true)

(def ^:const bytes-type (class (byte-array 0)))

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

(def ^:const socket-options
  {:sndhwm #(.setSndHWM ^ZMQ$Socket %1 %2)})

(def ^:const version
  {:major (ZMQ/getMajorVersion)
   :minor (ZMQ/getMinorVersion)
   :patch (ZMQ/getPatchVersion)})

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

(defn- receive!
  ([^ZMQ$Socket socket deserialize-fn]
   (receive! socket deserialize-fn (.recv socket) false))
  ([^ZMQ$Socket socket deserialize-fn frame]
   (receive! socket deserialize-fn frame true))
  ([^ZMQ$Socket socket deserialize-fn frame first?]
   (let [data (deserialize-fn frame)]
     (if (.hasReceiveMore socket)
       (cons data (lazy-seq (receive! socket deserialize-fn)))
       (if first? data (list data))))))

(defn closeable-lock
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
    (->> (.recv socket)
         (receive! socket deserialize-fn)
         box)))

;;; HACK: Polling is not a good idea, but the only way I could get it to work
(defmethod take! :default
  [^ZMQ$Socket socket deserialize-fn closed ^Lock handler]
  (when-not @closed
    (with-open [^AutoCloseable lock (closeable-lock handler)]
      (when (impl/active? handler)
        (let [poll (fn []
                     (with-open [^AutoCloseable lock (closeable-lock handler)]
                       (when-let [message (and (impl/active? handler)
                                               (.recv socket ZMQ/NOBLOCK))]
                         (let [commit (impl/commit handler)]
                           (->> message
                                (receive! socket deserialize-fn)
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
      (send! socket message serialize-fn)
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
  (take! [_ handler]
         (take! socket deserialize-fn closed handler))
  impl/WritePort
  (put! [_ message handler]
        (put! socket closed message serialize-fn handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn- read-write-channel [socket serialize-fn deserialize-fn]
  (ReadWriteChannel. socket serialize-fn deserialize-fn (atom false)))

(deftype ReadOnlyChannel
  [^ZMQ$Socket socket deserialize-fn closed]
  impl/ReadPort
  (take! [_ handler]
         (take! socket deserialize-fn closed handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn- read-only-channel [socket deserialize-fn]
  (ReadOnlyChannel. socket deserialize-fn (atom false)))

(deftype WriteOnlyChannel
  [^ZMQ$Socket socket serialize-fn closed]
  impl/WritePort
  (put! [_ message handler] (put! socket closed message serialize-fn handler))
  impl/Channel
  (closed? [_] @closed)
  (close! [_] (close! socket closed)))

(defn- write-only-channel [socket serialize-fn]
  (WriteOnlyChannel. socket serialize-fn (atom false)))

(defn- init-socket [socket-type bind-or-connect transport endpoint options]
  (let [socket (.createSocket context (socket-type socket-types))
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
  [socket-type bind-or-connect transport endpoint & options]
  (-> (init-socket socket-type bind-or-connect transport endpoint options)
      (read-write-channel serialize-data deserialize)))

(defn pub-chan
  [bind-or-connect transport endpoint & options]
  (-> (init-socket :pub bind-or-connect transport endpoint options)
      (write-only-channel serialize-data)))

(defn sub-chan
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
    (-> (init-socket :sub bind-or-connect transport endpoint options)
        (subscribe topics serialize-topic)
        (read-only-channel deserialize))))

(defn xpub-chan
  [bind-or-connect transport endpoint & options]
  (-> (init-socket :xpub bind-or-connect transport endpoint options)
      (write-only-channel serialize-data)))

(defn xsub-chan
  [bind-or-connect transport endpoint & options]
  (-> (init-socket :xsub bind-or-connect transport endpoint options)
      (read-only-channel deserialize)))

(defn push-chan
  [bind-or-connect transport endpoint & options]
  (-> (init-socket :push bind-or-connect transport endpoint options)
      (write-only-channel serialize-data)))

(defn pull-chan
  [bind-or-connect transport endpoint & options]
  (-> (init-socket :pull bind-or-connect transport endpoint options)
      (read-only-channel deserialize)))

(defn chan-proxy
  ([frontend backend]
   (chan-proxy frontend backend nil))
  ([frontend backend capture]
   (letfn [(pipe [in out capture]
                 (async/go-loop
                  []
                  (let [data (async/<! in)]
                    (when capture
                      (async/>! capture data))
                    (async/>! out data)
                    (recur))))]
     (pipe frontend backend capture)
     (pipe backend frontend capture))))
