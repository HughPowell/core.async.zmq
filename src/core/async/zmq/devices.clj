; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns ^{:skip-wiki true}
core.async.zmq.devices
  (:require [clojure.core.async :as async])
  (:refer-clojure :exclude [proxy]))

(set! *warn-on-reflection* true)

(defn proxy
  ([frontend backend]
   (proxy frontend backend nil))
  ([frontend backend capture]
   (letfn [(pipe [out capture data]
             (when capture (async/put! capture data))
             (async/put! out data))]
     (async/go-loop []
       (async/alt!
         frontend ([msg] (pipe backend capture msg))
         backend ([msg] (pipe frontend capture msg)))
       (recur)))))
