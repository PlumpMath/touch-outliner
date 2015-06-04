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
    (:import))

(enable-console-print!)

;;; state
;;; ------------------------------------------------------------------------------

(def initial-state
  {:mode     :tree
   :dragging nil
   :over-idx false
   :entries ["Red" "Green" "Blue" "Yellow" "Black" "White" "Orange"]
   })

;;; handlers
;;; ------------------------------------------------------------------------------

(register-handler                
 :initialize                   
 (fn 
   [db _]
   (merge db initial-state)))

(register-handler                
 :drag-start
 (fn 
   [db [_ n]]
   (assoc db :dragging n)))

(register-handler                
 :drag-over
 (fn 
   [db [_ over-idx]]
   (if (= over-idx "placeholder")
     db
     (assoc db :over-idx (js/parseInt over-idx 10)))))

(register-handler                
 :drag-end
 (fn 
   [{:keys [over-idx dragging] :as db} [_ n]]
   (-> db
       (update-in [:entries]
                  (fn [entries]
                  (let [disj-entries (into
                                      (subvec entries 0 dragging)
                                      (subvec entries (inc dragging)))]
                    (println (entries over-idx))

                    (into
                     (conj
                      (subvec disj-entries 0 over-idx)
                      (entries dragging))
                     (subvec disj-entries over-idx))
                    )
                    ))
       (assoc :over-idx false :dragging false)
       )))


;;; subscriptions
;;; ------------------------------------------------------------------------------

(register-sub :entries (fn [db _] (reaction (:entries @db))))
(register-sub :over-idx (fn [db _] (reaction (:over-idx @db))))
(register-sub :dragging (fn [db _] (reaction (:dragging @db))))

;;; components 
;;; ------------------------------------------------------------------------------

(defn sortable-list []
  (let [entries (subscribe [:entries])
        over-idx (subscribe [:over-idx])
        dragging (subscribe [:dragging])
        ]
    [:ul
     {:on-drag-over (fn [ev]
                      (.preventDefault ev)
                      (dispatch [:drag-over (.. ev -target -dataset -id)]))}
     (doall (for [[n entry] (map-indexed (fn [idx entry] [idx entry]) @entries)]
              (list
               (if (= @over-idx n)
                 [:li {:key "placeholder"
                       :class "placeholder"
                       :data-id "placeholder"}]
                 "")
               [:li
                {:draggable true
                 :style {:display (if (= @dragging n) "none" "block")}
                 :key n
                 :data-id n
                 :on-drag-start (fn [ev]
                                  (set! (.. ev -dataTransfer -effectAllowed) "move")
                                  (.setData (.. ev -dataTransfer)
                                            "text/html"
                                            (.-currentTarget ev))
                                  (dispatch [:drag-start n]))
                 :on-drag-end #(dispatch [:drag-end n])
                 }
                entry]
               )))
     ]
    ))

(defn application []
  [:div [sortable-list]])



;;; run
;;; ------------------------------------------------------------------------------

(defonce intialize
  (do
    (dispatch [:initialize])
    (.addEventListener js/window "keydown" 
                       #(case (.. % -keyCode)
                          :noop
                          ))))

(defn ^:export run
  []
  (reagent/render [application]
                  (js/document.getElementById "app")))
