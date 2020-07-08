(ns util
  (:require [cljs.core.async :refer-macros [go]]
            [cljs.core.async :refer [<! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]))

(enable-console-print!)

(def fs (js/require "fs"))
(def fspromise (.-promises fs))
(def process (js/require "process"))

(defn path-exists? [path]
  (try
    (do
      (.accessSync fs path)
      true)
    (catch js/Error err
      false)))

(defn read-file-async [path]
  (go (try
        (<p! (.readFile fspromise path))
        (catch js/Error err
          (println (ex-cause err))
          nil))))

(defn write-file-async! [path content]
  (go (try
        (<p! (.writeFile fspromise path content)))))

(defn prdr
  "little utility wrapper for console.dir"
  [& args]
  (.dir js/console args))

(defn exit [msg ok?]
  (println msg)
  (.exit process
         (if ok? 0 233)))
