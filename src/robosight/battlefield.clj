(ns robosight.battlefield
  (:require   (clojure.core [async :as async :refer [>!! <!! >! <!]])
              (clojure.java [io    :as io])
              (robosight    [core  :as robosight]))
  (:import    (java.io              PrintWriter)
              (java.util.concurrent TimeUnit))
  (:gen-class :name com.tail_island.robosight.Battlefield
              :main true))

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
         (if-not (some #(every? robosight/broken? (second %)) (group-by robosight/team (filter robosight/team objects)))
           (recur (tick objects))))
       robosight/initial-objects)

      (catch java.util.concurrent.TimeoutException ex
        (.printStackTrace ex)
        (doseq [process processes]
          (.waitFor process 1 TimeUnit/SECONDS)
          (when (.isAlive process)
            (.destroy process))))
      
      (finally
        (doseq [closeable (concat ins outs errs)]
          (.close closeable))
        (doseq [process processes]
          (.waitFor process 1 TimeUnit/SECONDS)
          (when (.isAlive process)
            (.destroy process)))
        (shutdown-agents)))))
