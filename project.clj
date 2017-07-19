(defproject com.tail-island/robosight-battlefield "0.1.0"
  :description  "FIXME: write description"
  :url          "http://example.com/FIXME"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure            "1.8.0"]
                 [org.clojure/core.async         "0.3.442"]
                 [org.clojure/tools.logging      "0.4.0"]
                 [com.tail-island/robosight-core "0.1.0"]]
  :aot          :all
  :main         com.tail_island.robosight.Battlefield
  :plugins      [[lein-checkouts "1.1.0"]])
