(ns app
  (:require [cljs.core.async :refer-macros [go]]
            [cljs.core.async :refer [<! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.tools.cli :refer [parse-opts]]
            [util :refer [read-file-async
                          write-file-async!
                          prdr
                          path-exists?]]))

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
        import-groups (filter #(= IMPORT-GROUP-NAME (.-name %)) subgroups)]
    (cond
      (= (count import-groups) 1) (first import-groups)
      (> (count import-groups) 1) (first import-groups) ; would be better if could merge duplicate importgroups
      (= (count import-groups) 0) (.createGroup kdbx-handle default-group IMPORT-GROUP-NAME))))

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

(def cli-opts
  [["-d" "--dashlane-export EXPORTPATH" "Path to dashlane export json"
    :validate [#(path-exists? %) "Must refer to a file that exists"]]
   ["-k" "--keepass-db KDBXPATH" "Path to input keepass database"
    :validate [#(path-exists? %) "Must refer to a file that exists"]]
   ["-p" "--keepass-pw KDBXPASS" "Password for kdbx database"]
   ["-o" "--output KDBXOUTPATH" "Output path for new kdbx with imported entries"]
   ["-h" "--help"]])

(defn main [& cli-args]
  (println "preparing to convert dashlane export!")
  (go
    (let [dash-accounts (<! (load-dashlane-export "DashlaneExport.json"))
          kdbx-handle (<! (load-kdbx-db
                           "Passwords.kdbx"
                           "jarjarbinksslaughtermongrel24"))]
      (doseq [entry dash-accounts]
        (do
          (let [title (get entry "title")]
            (println (str "importing entry with title: " title))
            (add-dashlane-entry! kdbx-handle entry))))
      (save-kdbx-db! kdbx-handle "Newdatabase.kdbx")
      (println "import successful")
      (println "saved new database to 'Newdatabase.kdbx'"))))
