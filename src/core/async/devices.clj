; Copyright (c) the Contributors as noted in the AUTHORS file.
; This file is part of Global Domination. Resistance is useless.

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at http://mozilla.org/MPL/2.0/.

(ns ^{:skip-wiki true}
  core.async.devices
  (:require [clojure.core.async :as async]))

(set! *warn-on-reflection* true)

(defn capturing-proxy
  ([frontend backend]
   (capturing-proxy frontend backend nil))
  ([frontend backend capture]
   (letfn [(pipe [in out capture]
                  (let [data (async/<! in)]
                    (when capture
                      (async/>! capture data))
                    (async/>! out data)))]
     (async/go-loop
      []
      (async/alt!
       frontend ([msg] (pipe frontend backend capture))
       backend ([msg] (pipe backend frontend capture)))
      (recur)))))
