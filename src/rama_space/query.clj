(ns rama-space.query
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.path :as path]))

;; ---------- query pstate examples ----------

(defn get-pwd-hash
  [profiles-pstate user-id]
  (->> profiles-pstate
       (path/select-one (keypath user-id :pwd-hash))))

^:rct/test
(comment
  (def profiles {"bob" {:pwd-hash "hash1"}
                 "alice" {:pwd-hash "hash2"}})
  (get-pwd-hash profiles "bob") ;=> "hash1"
  (get-pwd-hash profiles "mael") ;=> nil
  )

(defn get-profile
  [profiles-pstate user-id]
  (let [profile (-> [(path/keypath user-id)
                     (path/submap [:display-name :location :bio :email :profile-pic :joined-at-millis])]
                    (path/select-one profiles-pstate))]
    (when (seq profile) profile)))

^:rct/test
(comment
  (def profiles {"bob" {:display-name "bob"
                        :pwd-hash "hash1"
                        :location "singapore"
                        :bio "some bio"
                        :email "bob@mail.sg"
                        :profile-pic "a"
                        :joined-at-millis 1704096000000}
                 "alice" {:display-name "alice"
                          :pwd-hash "hash2"
                          :location "france"
                          :bio "some other bio"
                          :email "alice@mail.fr"
                          :profile-pic "b"
                          :joined-at-millis 1704243600000}})
  (-> (get-profile profiles "bob") vals count (= 6)) ;=> true
  (get-profile profiles "mael") ;=> nil
  )

(defn get-count*
  [pstate user-id]
  (-> [(path/keypath user-id)
       (path/view count)]
      (path/select-one pstate)))

(defn get-friends-count
  [friends-pstate user-id]
  (get-count* friends-pstate user-id))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn get-posts-count
  [posts-pstate user-id]
  (get-count* posts-pstate user-id))

^:rct/test
(comment
  (def friends {"bob" #{"alice" "hugo"}
                "alice" #{}})
  (get-friends-count friends "bob") ;=> 2
  (get-friends-count friends "alice") ;=> 0
  )

(defn are-friends?
  [friends-pstate user-id-1 user-id-2]
  (-> [(path/keypath user-id-1)
       (path/set-elem user-id-2)]
      (path/select friends-pstate)
      seq
      some?))

^:rct/test
(comment
  (def friends {"bob" #{"alice" "hugo"}
                "alice" #{"bob"}})
  (are-friends? friends "bob" "alice") ;=> true
  (are-friends? friends "alice" "bob") ;=> true
  (are-friends? friends "alice" "hugo") ;=> false
  )

;; TODO: iterate through friends in the order in which the friendships were created
(defn get-friends-page*
  "Returns a page of `max-amt` friends of `user-id`.
   The friends are sorted alphabetically by their ids."
  [pstate user-id {:keys [start max-amt]
                   :or {start "" max-amt 20}}]
  (-> [(path/keypath user-id)
       (path/sorted-set-range-from start {:max-amt max-amt
                                          :inclusive? false})]
      (path/select-one pstate)))

(defn get-friends-page
  [friends-pstate user-id opt]
  (get-friends-page* friends-pstate user-id opt))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn get-outgoing-friend-requests
  [outgoing-friend-requests-pstate user-id opt]
  (get-friends-page* outgoing-friend-requests-pstate user-id opt))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn get-incoming-friend-requests
  [incoming-friend-requests-pstate user-id opt]
  (get-friends-page* incoming-friend-requests-pstate user-id opt))

^:rct/test
(comment
  ;; we make sure to use a sorted set to allow pagination
  (def friends {"bob" (sorted-set "alice" "hugo" "lucas" "paul")})
  (get-friends-page friends "bob" {:max-amt 2}) ;=> #{"alice" "hugo"}
  (get-friends-page friends "mael" {:max-amt 2}) ;=> #{}
  )

(defn get-num-profile-views
  "Returns the sum of the profile views of `user-id` 
   from `start-hour-bucket` included to `end-hour-bucket` exluded."
  [profile-views-pstate user-id start-hour-bucket end-hour-bucket]
  (-> [(path/keypath user-id)
       (path/sorted-map-range start-hour-bucket end-hour-bucket)
       (path/subselect path/MAP-VALS)
       (path/view #(reduce + %))]
      (path/select-one profile-views-pstate)))

^:rct/test
(comment
  (def profile-views {"bob" (sorted-map
                             2 50 0 75 1 100)
                      "alice" (sorted-map
                               1 90 2 40 0 20)})
  (get-num-profile-views profile-views "bob" 0 2) ;=> 175
  (get-num-profile-views profile-views "mael" 0 2) ;=> 0
  )
