(ns ^:figwheel-always outliner.core
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require
     [reagent.core :as reagent :refer [atom]]
     [re-frame.core :refer [register-handler
                            path
                            register-sub
                            dispatch
                            dispatch-sync
                            subscribe]]
     ))

(enable-console-print!)

(.initializeTouchEvents js/React true)

;;; state
;;; ------------------------------------------------------------------------------


(def initial-state
  {:mode      :tree
   :state     :static
   :selected  nil 
   :dragged   nil
   :hovered   nil
   :window-width 0
   :entries (vec (map #(apply str (take 5 (repeat (str % " ")))) (range 40))) 
   :bounding-boxes []
   })

;;; util
;;; ------------------------------------------------------------------------------

(defn v-remove [v n]
  (into (subvec v 0 n) (subvec v (inc n))))

(defn move-within-vector [v from to]
  (if (= from to) v
      (let [elem      (v from)
            without-v (v-remove v from) 
            adj-to (if (< from to) (dec to) to)]
        (into (conj (subvec without-v 0 adj-to) elem) (subvec without-v adj-to)))))


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
   (assoc db :selected (js/parseInt idx 10)
             :state    :pressed)
   ))

(register-handler
 :window-mouse-up
 (fn [db _]
   (cond
     
     (= :pressed (:state db))
     
     (assoc db
            :state :static
            :hovered nil
            :selected nil
          )
     
     (= :dragging (:state db))
     (do
       (println (:hovered db))
       (assoc db
              :entries (if (= (:hovered db) :outside)
                         (conj (v-remove (:entries db) (:selected db)) ((:entries db) (:selected db)))
                         (move-within-vector (:entries db) (:selected db) (:hovered db)))
             :state :static
             :hovered nil
             :selected nil
             ))
     
     :else (assoc db
                  :state :static
                  :hovered nil
                  :selected nil
             )
     )))

(register-handler                
 :mounted-list
 (fn 
   [db [_ ul-dom-node]]
   (assoc db :ul-dom-node ul-dom-node)))

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
   [db [_ page-x page-y]]
   
   (cond
     
     (and (:selected db)
          (= :pressed (:state db)))
     
     (assoc db
            :state :dragging
            :page-x page-x
            :page-y page-y
            )
     
     (and (:selected db)
          (= :dragging (:state db)))
     
     (let [bounding-boxes
           (doall
            (for [li (array-seq (.getElementsByTagName (:ul-dom-node db) "li"))]
              (let [top    (.-offsetTop li)
                    height (.-offsetHeight li)
                    n (.. li -dataset -idx)
                    ]
                [top height n]
                )))
           [_ _ hovered-box-idx] 
           (or
            (find-first
             (fn [[y h _]]
               (< (- page-y 10) (+ y h)))
             bounding-boxes)
            [nil nil :outside])
           ]
       (assoc db
              :hovered (if (= :outside hovered-box-idx)
                         :outside
                         (js/parseInt hovered-box-idx 10))
              :page-x page-x
              :page-y page-y
              ))
     
     :else db
     )))

(register-handler
 :activate
 (fn [db [_ n id]]
   (if (nil? (:selected db))
     (assoc db :selected {:idx n :touchid id})
     db)))

;;; subscriptions
;;; ------------------------------------------------------------------------------

(register-sub :entries (fn [db _] (reaction (:entries @db))))
(register-sub :window-width (fn [db _] (reaction (:window-width @db))))
(register-sub :selected (fn [db _] (reaction (:selected @db))))
(register-sub :hovered (fn [db _] (reaction (:hovered @db))))

;;; components 
;;; ------------------------------------------------------------------------------

(defn list-element []
  (fn
    [n entry]
    (let [selected (subscribe [:selected])
          hovered   (subscribe [:hovered])]
      [:li
       {:data-idx n
        :key n
        :on-mouse-down #(dispatch [:item-mouse-down n ])
        :style {:border-top
                (if
                    (= n @hovered)
                    "1px solid red"
                    "1px solid transparent"
                    )}
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

(def sortable-list 
  (with-meta
    (fn [] sortable-list-component)
    {:component-did-mount (fn [this] (dispatch [:mounted-list (reagent/dom-node this)]))}
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
                         (dispatch [:window-mouse-move (.. ev -pageX) (.. ev -pageY)])
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
