(ns ^:figwheel-always outliner.core
    (:require-macros [reagent.ratom :refer [reaction]]
                                        ; [secretary.core :refer [defroute]  ]
                     )
    (:require
     [reagent.core :as reagent :refer [atom]]
     [re-frame.core :refer [register-handler
                            path
                            register-sub
                            dispatch
                            dispatch-sync
                            subscribe]]
     )
    )

(enable-console-print!)

(.initializeTouchEvents js/React true)

;;; state
;;; ------------------------------------------------------------------------------

(def initial-state
  {:mode     :tree
   :drag-idx nil
   :over-idx false
   :window-width 0
   :entries ["Red" "Green" "Blue" "Yellow" "Black" "White" "Orange"]
   :bounding-boxes []
   :hovered nil
   })

;;; handlers
;;; ------------------------------------------------------------------------------

(register-handler                
 :initialize                   
 (fn 
   [db _]
   (merge db initial-state)))

(register-handler                
 :window-width
 (fn 
   [db [_ w]]
   (assoc db :window-width w)))

(register-handler                
 :bounding-boxes
 (fn 
   [db [_ boxes]]
   (assoc db :bounding-boxes boxes)))

(defn find-first [f coll]
  (first (drop-while (complement f) coll)))

(register-handler                
 :touchmove
 (fn 
   [db [_ [x y]]]
   (if (nil? (:activated db))
     db
     (let [hover-idx
           (get (find-first (fn [[box-top box-bottom n]]
                              (and (<= box-top y)
                                   (> box-bottom y)
                                   ))
                            (:bounding-boxes db)) 2)]
       (if (nil? hover-idx)
         db
         (assoc db :hovered hover-idx))
       ))))

(register-handler
 :activate
 (fn [db [_ n id]]
   (if (nil? (:activated db))
     (assoc db :activated {:idx n :touchid id})
     db)))

(defn vec-swap [v idx1 idx2]
  (assoc v idx2 (v idx1) idx1 (v idx2)))

(register-handler
 :touchend
 (fn [db [_ id]]
   (if (or (nil? (:activated db)) (not= id (:touchid (:activated db))))
     db
     (let [entries (:entries db)
           hovered (:hovered db)
           new-entries (if (nil? hovered)
                         entries
                         (vec-swap entries
                                   (js/parseInt hovered 10)
                                   (js/parseInt (:idx (:activated db)) 10)))]

       (assoc db
              :activated nil
              :hovered nil
              :entries new-entries))
     )))

;;; subscriptions
;;; ------------------------------------------------------------------------------

(register-sub :entries (fn [db _] (reaction (:entries @db))))
(register-sub :window-width (fn [db _] (reaction (:window-width @db))))
(register-sub :activated (fn [db _] (reaction (:activated @db))))
(register-sub :hovered (fn [db _] (reaction (:hovered @db))))

;;; components 
;;; ------------------------------------------------------------------------------

(defn list-element []
  (fn
    [n entry]
    (let [activated (subscribe [:activated])
          hovered (subscribe [:hovered])]
      [:li
       {:data-idx n
        :key n
        :on-touch-start #(dispatch [:activate n (.-identifier (aget (.-targetTouches %) 0))])
        :style {:background (cond (= n (:idx @activated)) "red"
                                  (= (str n) @hovered) "blue"
                                  :else "#f2f2f2")}
        }
      entry])))

(defn sortable-list-component []
  (let [entries (subscribe [:entries])
        window-width (subscribe [:window-width])]
    [:ul
     {:data-ww @window-width}
     (for [[n entry](map-indexed vector @entries)]
       ^{:key (str "entry" n)}
       [list-element n entry])]
    ))

(defn update-dims
  [this]
  (let [parent-ul (reagent/dom-node this)
        bounding-boxes 
        (doall
         (for [li (array-seq (.getElementsByTagName parent-ul "li"))]
           (let [client-rect (.getBoundingClientRect li)
                 top    (.-top client-rect)
                 height (.-height client-rect)
                 n (.. li -dataset -idx)
                 ]
             [top (+ top height) n]
             )))]
    (dispatch [:bounding-boxes bounding-boxes])
    ))

(def sortable-list 
  (with-meta
    (fn [] sortable-list-component)
    {:component-did-mount update-dims
     :component-did-update update-dims}
    ))

(defn application []
  [:div [sortable-list]])


;;; run
;;; ------------------------------------------------------------------------------

(defonce intialize
  (do
    (dispatch [:initialize])
    (dispatch [:window-width (.-innerWidth js/window)])
    (.addEventListener js/window "keydown" 
                       #(case (.. % -keyCode)
                          :noop
                          ))
    
    (.addEventListener js/window "resize"
                       (fn [ev] (dispatch [:window-width (.-innerWidth js/window)])))
    
    (.addEventListener js/window "touchmove"
                       (fn [ev]
                         (.preventDefault ev)
                         (let [tch (aget (.-targetTouches ev) 0)]
                           (dispatch [:touchmove [(.-pageX tch) (.-pageY tch)]]))))
    
    (.addEventListener js/window "touchend"
                       (fn [ev]
                         (let [tch (aget (.-changedTouches ev) 0)]
                           (dispatch [:touchend (.-identifier tch)]))))
    ))

(defn ^:export run
  []
  (reagent/render [application]
                  (js/document.getElementById "app")))
