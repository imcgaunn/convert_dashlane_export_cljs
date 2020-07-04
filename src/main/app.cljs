(ns app
  (:require [cljs.core.async :refer-macros [go]]
            [cljs.core.async :refer [<! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [util :refer [read-file-async write-file-async! prdr]]))

(enable-console-print!)

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
(def IMPORT-GROUP-NAME "DashlaneImportGroup")

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
        (prdr (ex-cause err))
        nil))))

(defn buf->arraybuf [buf]
  (let [offset (.-byteOffset buf)
        bytelen (.-byteLength buf)
        arraybuf (.slice (.-buffer buf)
                         offset
                         (+ offset bytelen))] arraybuf))

(defn load-kdbx-db [kdbx-path pw]
  (go
    (let [protectedpw (.fromString kdbxweb.ProtectedValue pw)
          creds (kdbxweb.Credentials. protectedpw)
          buf (<! (read-file-async kdbx-path))
          kdbx-arraybuf (buf->arraybuf buf)
          handle (<! (get-db-handle-with-creds kdbx-arraybuf creds))]
      handle)))

(defn get-or-create-import-group! [kdbx-handle]
  (let [default-group (.getDefaultGroup kdbx-handle)
        subgroups (.-groups default-group)
        import-group (some #(= IMPORT-GROUP-NAME %) subgroups)]
    (if (nil? import-group)
      (.createGroup kdbx-handle default-group IMPORT-GROUP-NAME)
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
  (println "preparing to convert dashlane export!")
  (go
    (let [dash-accounts (<! (load-dashlane-export "DashlaneExport.json"))
          kdbx-handle (<! (load-kdbx-db
                           "Passwords.kdbx"
                           "jarjarbinksslaughtermongrel24"))]
      (prdr kdbx-handle))))
