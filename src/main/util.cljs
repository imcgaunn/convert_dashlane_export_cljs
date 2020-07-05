(ns util
  (:require [cljs.core.async :refer-macros [go]]
            [cljs.core.async :refer [<! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]))

(enable-console-print!)

(def fs (js/require "fs"))
(def fspromise (.-promises fs))

(defn path-exists? [path]
  (go
    (try
      (do
        (<p! (.access fspromise path))
        true)
      (catch js/Error err
        (println (ex-cause err))
        false))))

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
