(defproject outliner "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3211"]
                 ; [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 ; app specific
                 [re-frame "0.4.1"]
                 [reagent "0.5.0"]
                 ; [secretary "1.2.3"]
                 ]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.3.3"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]

              :figwheel {:on-jsload "outliner.core/run"}

              :compiler {:main outliner.core
                         :asset-path "js/compiled/out"
                         :output-to "resources/public/js/compiled/outliner.js"
                         :output-dir "resources/public/js/compiled/out"
                         :source-map-timestamp true }}
             {:id "min"
              :source-paths ["src"]
              :compiler {:output-to "resources/public/js/compiled/outliner.js"
                         :main outliner.core
                         :optimizations :advanced
                         :pretty-print false}}]}

  :figwheel {
             :css-dirs ["resources/public/css"]
             :nrepl-port 7888
             })
