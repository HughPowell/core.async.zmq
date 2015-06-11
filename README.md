<A name="#toc1" title="core.async.zmq" />
# core.async.zmq


<!--
[![Build Status](https://travis-ci.org/HughPowell/core.async.zmq.png?branch=master)](https://travis-ci.org/HughPowell/core.async.zmq)
-->

<A name="#toc2" title="Contents" />
## Contents

**<a href="#toc3">An incredibly cunning plan</a>**

**<a href="#toc4">Getting up and running</a>**

**<a href="#toc5">Ownership and License</a>**

<A name="toc3" title="An incredibly cunning plan" />
### The incredibly cunning plan

Right then, [ZeroMQ](http://zguide.zeromq.org/page:all) rocks my socks having taken "a normal TCP socket, injected it with a mix of radioactive isotopes stolen from a secret Soviet atomic research project, bombarded it with 1950-era cosmic rays, and put it into the hands of a drug-addled comic book author with a badly-disguised fetish for bulging muscles clad in spandex". It also passes messages around processes, boxes and the interwebs in its spare time. As I'm currently going through my functional programming crisis and Clojure has become my weapon of choice I've decided to write a ZeroMQ binding, since there aren't any available (especially not [cljzmq](https://github.com/zeromq/cljzmq), [zmq-async](https://github.com/lynaghk/zmq-async), [clj-0MQ](https://github.com/AndreasKostler/clj-0MQ) or [ezmq](https://github.com/tel/ezmq)). Now, [core.async](https://github.com/clojure/core.async) is awesome (but still alpha), it's got channels and go macros and is just about the second coming when it comes to doing async work in clojure (given the appropriate situation, obviously). So, the incredibly cunning plan is to suppliment the ManyToManyChannel of this alpha stage project with one using ZeroMQ as the underlying transport mechanism and blow open core.async across the interwebs. Sound like fun? Come jump in, the sooner we realise this is impossible the better.


<A name="toc4" title="Getting up and running" />
### Getting up and running

First off we need the brand spanking new, not even released yet (yeah, that sounds safe) core.async so do the following so that core.async.zmq can access it

    git clone https://github.com/clojure/core.async.git
    cd core.async
    lein install
    cd ..

Then we build the big ball of chaos itself

    git clone https://github.com/HughPowell/core.async.zmq.git
    cd core.async.zmq
    lein install
    cd ..

By default core.async.zmq uses the [JeroMQ](https://github.com/zeromq/jeromq) ZeroMQ Java implementation, so if you're using [Leiningen](https://github.com/technomancy/leiningen) add the following to your project.clj file


    :dependencies [[org.clojure/clojure "1.6.0"]
                   [org.clojure/core.async "0.1.0-SNAPSHOT"]
                   [org.zeromq/jeromq "0.3.5-SNAPSHOT"]
                   [org.clojure/core.async.zmq "0.1.0-SNAPSHOT"]]
    :repositories [["releases" {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                                :username [:gpg :env/NEXUS_USERNAME]
                                :password [:gpg :env/NEXUS_PASSWORD]}]
                   ["snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots"
                                 :username [:gpg :env/NEXUS_USERNAME]
                                 :password [:gpg :env/NEXUS_PASSWORD]
                                 :update :always}]])

[JZMQ](https://github.com/zeromq/jzmq) should also work if you can install it, just replace the dependency on JeroMQ with the following

    [org.zeromq/jzmq "3.0.1"]



<A name="toc5" title="Ownership and License" />
### Ownership and License

The contributors are listed in AUTHORS. This project uses the MPL v2 license, see LICENSE.

core.async.zmq uses the [C4.1 (Collective Code Construction Contract)](http://rfc.zeromq.org/spec:22) process for contributions.

core.async.zmq uses the [clojure-style-guide](https://github.com/bbatsov/clojure-style-guide) for code style.

To report an issue, use the [core.async.zmq issue tracker](https://github.com/HughPowell/core.async.zmq/issues) at github.com.
