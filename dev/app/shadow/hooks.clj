(ns app.shadow.hooks
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [shadow.build :as build]
   [shadow.cljs.util :as util]))

(defn async-process [cmd]
  (let [;; Redirect output to stdout.
        ;; inheritIO also redirects stdin, some tools might not like that.
        builder (-> (ProcessBuilder. ^"[Ljava.lang.String;" (into-array String cmd))
                    (.redirectError java.lang.ProcessBuilder$Redirect/INHERIT)
                    (.redirectOutput java.lang.ProcessBuilder$Redirect/INHERIT))
        process (.start builder)]
    ;; Also stop processes when shadow-cljs is stopped.
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (.destroy process))))
    process))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn esbuild-watch
  {:shadow.build/stages #{:configure}}
  [build-state bundle outfile]
  (let [base-cmd ["npx" "esbuild" "--watch" "--bundle" bundle (str "--outfile=" outfile)]
        watch-process (async-process base-cmd)]
    (apply shell/sh ["touch" bundle])
    (util/log build-state {:type ::esbuild-watch :result watch-process})
    (async-process base-cmd)
    (assoc build-state ::esbuild-watch watch-process)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn esbuild
  {:shadow.build/stages #{:compile-prepare :flush}}
  [{::build/keys [mode] :as build-state} bundle outfile]
  (let [base-cmd ["npx" "esbuild" "--bundle" bundle (str "--outfile=" outfile)]
        release-cmd (into base-cmd
                          ["--analyze"
                           "--metafile=.bundle.meta.json"
                           "--define:NODE_ENV=production"
                           "--minify"])
        result (if (= :release mode)
                 (apply shell/sh release-cmd)
                 (apply shell/sh base-cmd))]
    (if (zero? (:exit result))
      (do
        (util/log build-state {:type ::esbuild-result :result result})
        (assoc build-state ::esbuild result))
      (util/error build-state {:type ::esbuild-result :result result}))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn hashed-files
  {:shadow.build/stage :flush}
  [{::build/keys [mode] :as build-state} files]
  (doall
   (assoc build-state ::hashed-files
          (for [old-file-full-path files]
            (let [old-file (io/file old-file-full-path)
                  old-file-name (.getName old-file)]
              (if (= :release mode)
                (let [contents (slurp old-file-full-path)
                      old-file-path (.getParentFile old-file)
                      new-file-name (str (util/md5hex contents) "." old-file-name)
                      new-file-full-path (str old-file-path "/" new-file-name)
                      new-file (io/file new-file-full-path)]
                  (.renameTo old-file new-file)
                  {:old-file-full-path old-file-full-path
                   :old-file-name old-file-name
                   :new-file-full-path new-file-full-path
                   :new-file-name new-file-name})

                {:old-file-full-path old-file-full-path
                 :old-file-name old-file-name
                 :new-file-full-path old-file-full-path
                 :new-file-name old-file-name}))))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn build-index
  {:shadow.build/stage :flush}
  [{::build/keys [mode] :as build-state} source target]
  (let [source-file (io/file source)
        target-file (io/file target)]
    (cond
      (not (.exists source-file))
      (do (util/log build-state {:type ::source-does-not-exist :source source})
          build-state)

      ;; in dev mode we don't need to copy more than once
      (and (= :dev mode)
           (= (.lastModified source-file) (::source-last-mod build-state))
           (.exists target-file))
      build-state

      ;; only replace files with the hashed name if release
      (= :release mode)
      (let [html (slurp source-file)
            new-html (reduce
                      (fn [accum {:keys [old-file-name new-file-name]}]
                        (str/replace accum old-file-name new-file-name))
                      html
                      (::hashed-files build-state))]
        (io/make-parents target-file)
        (spit target-file new-html)
        (assoc build-state ::source-last-mod (.lastModified source-file)))

      (= :dev mode)
      (let [html (slurp source-file)]
        (io/make-parents target-file)
        (spit target-file html)
        (assoc build-state ::source-last-mod (.lastModified source-file))))))
