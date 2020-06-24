(ns app
  (:require [cljs.core.async :refer-macros [go]]
            [cljs.core.async :refer [<! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]))

(enable-console-print!)

(def fs (js/require "fs"))
(def fspromise (.-promises fs))
(def kdbxweb (js/require "kdbxweb"))

(def DASHLANE-ENTRY-FIELDS #{"domain"
                             "email"
                             "login"
                             "note"
                             "password"
                             "secondaryLogin"
                             "title"})
(def KEEPASS-ENTRY-FIELDS #{"Notes"
                            "Password"
                            "Title"
                            "URL"
                            "UserName"})

(defn read-file-async [path]
  (go (try
        (<p! (.readFile fspromise path))
        (catch js/Error err
          (println (ex-cause err))
          nil))))

(defn write-file-async! [path content]
  (go (try
        (<p! (.writeFile fspromise path content)))))

(defn load-dashlane-export [export]
  (go
    (let [export-content (<! (read-file-async export))
          export-json (js->clj (.parse js/JSON export-content))
          export-accounts (get export-json "AUTHENTIFIANT")] export-accounts)))

(defn get-db-handle-with-creds [kdbx-arraybuf creds]
  (go
    (try
      (<p! (.load kdbxweb.Kdbx kdbx-arraybuf creds))
      (catch js/Error err
        (println (ex-cause err))
        nil))))

(defn load-kdbx-db [kdbx pw]
  (go
    (let [protectedpw (.fromString kdbxweb.ProtectedValue pw)
          creds (kdbxweb.Credentials. protectedpw)
          buf (<! (read-file-async kdbx))
          arraybuf (.-buffer buf)
          handle (<! (get-db-handle-with-creds arraybuf creds))]
      handle)))

(defn get-or-create-import-group! [kdbx-handle]
  (let [default-group (.getDefaultGroup kdbx-handle)
        subgroups (.-groups default-group)
        import-group (some #(= "DashlaneImportGroup" %) subgroups)]
    (if (nil? import-group)
      (.createGroup kdbx-handle default-group "DashlaneImportGroup")
      import-group)))

(defn add-dashlane-entry! [kdbx-handle entry]
  (let [import-group (get-or-create-import-group! kdbx-handle)
        new-entry (.createEntry kdbx-handle import-group)
        new-entry-fields (.-fields new-entry)]
    (aset new-entry-fields "Notes" (get entry "note"))
    (aset new-entry-fields "Password" (get entry "password"))
    (aset new-entry-fields "Title" (get entry "title"))
    (aset new-entry-fields "URL" (get entry "domain"))
    (aset new-entry-fields "UserName" (or (get entry "login") (get entry "email")))
    new-entry))

(defn save-kdbx-handle [kdbx-handle]
  (go
    (let [db-data (<p! (.save kdbx-handle))
          db-data-buf (.from js/Buffer db-data)] db-data-buf)))

(defn save-kdbx-db! [kdbx-handle out-path]
  (go
    (let [data-buf (<! (save-kdbx-handle kdbx-handle))]
      (write-file-async! out-path data-buf))))

(defn main [& cli-args]
  (println "hello world"))