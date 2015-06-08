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

;;; state
;;; ------------------------------------------------------------------------------

(def initial-state
  {:mode     :tree
   :drag-idx nil
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
   (assoc db :drag-idx n)))

(register-handler                
 :drag-over
 (fn 
   [db [_ over-idx]]
   (case over-idx
     "placeholder" db 
     "dummy-element" (assoc db :over-idx :end)
     (assoc db :over-idx (js/parseInt over-idx 10)))))

(register-handler                
 :drag-end
 (fn 
   [{:keys [over-idx drag-idx] :as db} [_ n]]
   (-> db
       (update-in [:entries]
                  (fn [entries]
                    (let [dragged (entries drag-idx)
                          disj-entries (into
                                        (subvec entries 0 drag-idx)
                                        (subvec entries (inc drag-idx)))]
                      (if (= :end over-idx)
                        (conj disj-entries dragged)
                        (into
                         (conj (subvec disj-entries 0 over-idx) dragged)
                         (subvec disj-entries over-idx))))))
       (assoc :over-idx false :drag-idx false)
       )))

;;; subscriptions
;;; ------------------------------------------------------------------------------

(register-sub :entries (fn [db _] (reaction (:entries @db))))
(register-sub :over-idx (fn [db _] (reaction (:over-idx @db))))
(register-sub :drag-idx (fn [db _] (reaction (:drag-idx @db))))

;;; components 
;;; ------------------------------------------------------------------------------

(defn sortable-list []
  (let [entries (subscribe [:entries])
        over-idx (subscribe [:over-idx])
        drag-idx (subscribe [:drag-idx])
        ]
    [:ul
     {:id "listparent"
      :on-drag-over (fn [ev]
                      (.preventDefault ev)
                      (dispatch [:drag-over (.. ev -target -dataset -id)]))}
     (doall (for [[n entry] (map-indexed (fn [idx entry] [idx entry]) @entries)]
              (list
               (if (= @over-idx n)
                 [:li {:key "placeholder" :class "placeholder" :data-id "placeholder"}]
                 nil)
               [:li
                {:draggable true
                 :style {:display (if (= @drag-idx n) "none" "block")}
                 :key n
                 :data-id n
                 :on-drag-start (fn [ev]
                                  (set! (.. ev -dataTransfer -effectsAllowed) "move")
                                  (dispatch [:drag-start n]))
                 :on-drag-end (fn [ev]
                                (.preventDefault ev)
                                (set! (.. ev -dataTransfer -effectsAllowed) "move")
                                (dispatch [:drag-end n]))
                 }
                entry]
               )))
     (list
      (if (= @over-idx :end)
        [:li {:key "placeholder" :class "placeholder" :data-id "placeholder"}]
        nil)
      [:li {:key "dummy-element" :class "dummy-element" :data-id "dummy-element"}])
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
