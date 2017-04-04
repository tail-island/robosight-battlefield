(ns robosight.battlefield
  (:require   (clojure.core [async :as async :refer [>!! <!! >! <!]])
              (clojure.java [io    :as io])
              (robosight    [core  :as robosight]))
  (:import    (java.io PrintWriter))
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
    (doseq [err errs]
      (async/go
        (while true
          (binding [*out* *err*]
            (println (.readLine err))))))
    (try
      ((fn [objects]
         (println (pr-str objects))
         (if-not (some #(every? robosight/broken? (second %)) (group-by robosight/team (filter robosight/team objects)))
           (recur (tick objects))))
       robosight/initial-objects)
      (finally
        (doseq [closeable (flatten [ins outs])]
          (.close closeable))))))
