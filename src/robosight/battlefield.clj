(ns robosight.battlefield
  (:require   (clojure.core [async :as async :refer [>!! <!! >! <!]])
              (clojure.java [io    :as io])
              (robosight    [core  :as robosight]))
  (:import    (clojure.lang         ExceptionInfo)
              (java.io              PrintWriter)
              (java.util.concurrent TimeUnit))
  (:gen-class :name com.tail_island.robosight.Battlefield
              :main true))

(defn -main
  [& args]
  (let [processes (doall (map (fn [[cmd dir]]
                                (.exec (Runtime/getRuntime) cmd nil (io/as-file dir)))
                              (partition 2 args)))
        encoding  (System/getProperty "file.encoding")
        ins       (map #(PrintWriter. (io/writer (.getOutputStream %) :encoding encoding)) processes)
        outs      (map #(io/reader               (.getInputStream  %) :encoding encoding)  processes)
        errs      (map #(io/reader               (.getErrorStream  %) :encoding encoding)  processes)
        tick      (robosight/tick-fn ins outs)]
    (try
      (doseq [err errs]
        (async/go-loop []
          (when-let [s (.readLine err)]
            (binding [*out* *err*]
              (println s))
            (recur))))
      ((fn [state]
         (println (pr-str {:state state}))
         (if (robosight/game-finished? state)
           (println (pr-str {:winner (robosight/winner state)}))
           (recur (tick state))))
       robosight/initial-state)
      (catch clojure.lang.ExceptionInfo ex
        (case (:reason (ex-data ex))
          :timeout (println (pr-str {:winner (Math/abs (- (:timeout-team (ex-data ex)) 1))
                                     :note   :timeout}))
          (.printStackTrace ex))
        (doseq [process processes]
          (.destroy process)))
      (finally
        (doseq [closeable (concat ins outs errs)]
          (.close closeable))
        (doseq [process processes]
          (.waitFor process 1000 TimeUnit/MILLISECONDS)
          (.destroy process))
        (shutdown-agents)))))
