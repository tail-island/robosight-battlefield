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
                     "No game"))))

(defn -main
  [& args]
  (let [processes (doall (map (fn [[cmd dir]]
                                (.exec (Runtime/getRuntime) cmd nil (io/as-file dir)))
                              (partition 2 args)))
        ins       (doall (map #(PrintWriter. (io/writer (.getOutputStream %))) processes))
        outs      (doall (map #(io/reader               (.getInputStream  %))  processes))
        errs      (doall (map #(io/reader               (.getErrorStream  %))  processes))
        tick      (robosight/tick-fn ins outs)]
    (try
      (doseq [err errs]
        (binding [*out* *err*]
          (async/go-loop []
            (when-let [s (.readLine err)]
              (println s)
              (recur)))))
      ((fn [objects]
         (println (pr-str objects))
         (if (robosight/game-finished? objects)
           (print-winner (robosight/winner objects))
           (recur (tick objects))))
       robosight/initial-objects)

      (catch clojure.lang.ExceptionInfo ex
        (case (:reason (ex-data ex))
          :timeout (print-winner (Math/abs (- (:timeout-team (ex-data ex)) 1))))
        (doseq [process processes]
          (.destroy process)))

      (finally
        (doseq [closeable (concat ins outs errs)]
          (.close closeable))
        (doseq [process processes]
          (.waitFor process 1000 TimeUnit/MILLISECONDS)
          (when (.isAlive process)
            (.destroy process)))
        (shutdown-agents)))))
