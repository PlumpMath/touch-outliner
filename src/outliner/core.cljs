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
  {:mode      :tree
   :state     :static
   :selected  nil 
   :dragged   nil 
   :window-width 0
   :entries (vec (map #(apply str (take 5 (repeat (str % " ")))) (range 40))) 
   :bounding-boxes []
   })

;;; handlers
;;; ------------------------------------------------------------------------------

(register-handler                
 :initialize                   
 (fn 
   [db _]
   (merge db initial-state)))

(register-handler                
 :item-mouse-down
 (fn 
   [db [_ idx]]
   (assoc db :selected idx
             :state    :pressed)
   ))

(register-handler
 :window-mouse-up
 (fn [db _]
   (cond
     (= :pressed (:state db))
     (do
       (println "pressed " (:selected db))
       (assoc db
              :state :static
              :selected nil
              :dragged nil))
     
     :else db
     )))

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
 :window-mouse-move
 (fn 
   [db _]
   (cond
     
     (and (:selected db)
          (= :pressed (:state db)))
     (assoc db :state :dragging)
     
     :else db
     )))

(register-handler
 :activate
 (fn [db [_ n id]]
   (if (nil? (:activated db))
     (assoc db :activated {:idx n :touchid id})
     db)))

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
          hovered   (subscribe [:hovered])]
      [:li
       {:data-idx n
        :key n
        :on-mouse-down #(dispatch [:item-mouse-down n ])
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
    {;:component-did-mount update-dims
     ;:component-did-update update-dims
     }
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
    
    (.addEventListener js/window "mousemove"
                       (fn [ev]
                         (.preventDefault ev)
                         (dispatch [:window-mouse-move])
                         ))
    
    (.addEventListener js/window "mouseup"
                       (fn [ev]
                         (dispatch [:window-mouse-up])
                         ))
    ))

(defn ^:export run
  []
  (reagent/render [application]
                  (js/document.getElementById "app")))
