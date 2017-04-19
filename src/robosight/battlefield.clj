(ns robosight.battlefield
  (:require   (clojure.core [async :as async :refer [>!! <!! >! <!]])
              (clojure.java [io    :as io])
              (robosight    [core  :as robosight]))
  (:import    (clojure.lang         ExceptionInfo)
              (java.io              PrintWriter)
              (java.util.concurrent TimeUnit))
  (:gen-class :name com.tail_island.robosight.Battlefield
              :main true))

(defn- print-winner
  [winner]
  (println (pr-str (if winner
                     (format "%s team win!" (["Left" "Right"] winner))
                     "No game.."))))

(defn -main
  [& args]
  (let [processes (doall (map (fn [[cmd dir]]
                                (.exec (Runtime/getRuntime) cmd nil (io/as-file dir)))
                              (partition 2 args)))
        ins       (doall (map #(PrintWriter. (io/writer (.getOutputStream %) :encoding (System/getProperty "file.encoding"))) processes))
        outs      (doall (map #(io/reader               (.getInputStream  %) :encoding (System/getProperty "file.encoding"))  processes))
        errs      (doall (map #(io/reader               (.getErrorStream  %) :encoding (System/getProperty "file.encoding"))  processes))
        tick      (robosight/tick-fn ins outs)]
    (try
      (doseq [err errs]
        (binding [*out* *err*]
          (async/go-loop []
            (when-let [s (.readLine err)]
              (println s)
              (recur)))))
      ((fn [state]
         (println (pr-str state))
         (if (robosight/game-finished? state)
           (print-winner (robosight/winner state))
           (recur (tick state))))
       robosight/initial-state)

      (catch clojure.lang.ExceptionInfo ex
        (case (:reason (ex-data ex))
          :timeout (print-winner (Math/abs (- (:timeout-team (ex-data ex)) 1)))
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
