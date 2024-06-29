(ns rama-space.pstate-sch
  (:use [com.rpl.rama]))

(def profiles-sch (map-schema String ;; user-id
                              (fixed-keys-schema
                               {:display-name String
                                :email String
                                :profile-pic String
                                :bio String
                                :location String
                                :pwd-hash Integer
                                :joined-at-millis Long
                                :registration-uuid String})))

(def outgoing-friend-requests-sch (map-schema String ;; user-id
                                              (set-schema String
                                                          {:subindex? true}) ;; set of user-id
                                              ))

(def incoming-friend-requests-sch (map-schema String ;; user-id
                                              (set-schema String
                                                          {:subindex? true}) ;; set of user-id
                                              ))

(def friends-sch (map-schema String ;; user-id
                             (set-schema String
                                         {:subindex? true}) ;; set of user-id
                             ))

(def posts-sch
  (map-schema String ;; user-id
              (map-schema Long ;; post-id
                          (fixed-keys-schema
                           {:to-user-id String
                            :content String})
                          {:subindex? true})))

(def profile-views-sch (map-schema String ;; user-id
                                   (map-schema Long ;; hour-bucket
                                               Long  ;; count
                                               {:subindex? true})))

;; Attempt to represent the different pstates at an instant t for learning purposes
;; if we were to represent them as regular nested clojure data, it could look like this:
#_{:pstate/$$profiles (sorted-map "bob-id" {:display-name "bob"
                                            :email "bob@mail.sg"
                                            :profile-pic "pic"
                                            :location "Singapore"
                                            :bio "bio"
                                            :pwd-hash (hash "bob-pwd")
                                            :joined-at-millis (System/currentTimeMillis)
                                            :registration-uuid (str (random-uuid))}
                                  "alice-id" {:display-name "alice"
                                              :email "alice@mail.fr"
                                              :profile-pic "pic"
                                              :location "France"
                                              :bio "bio"
                                              :pwd-hash (hash "alice-pwd")
                                              :joined-at-millis (System/currentTimeMillis)
                                              :registration-uuid (str (random-uuid))}
                                  ;; mael and hugo not shown
                                  )
   :pstate/$$profile-views {"bob-id" {1 50 2 100}
                            "alice-id" {1 0 2 10}
                            "hugo-id" {1 100 2 102}
                            "mael-id" {1 60 2 70}}
   :pstate/$$outgoing-friend-requests {"bob-id" (sorted-set "alice-id" "hugo-id")
                                       "alice-id" (sorted-set)
                                       "hugo-id" (sorted-set)
                                       "mael-id" (sorted-set)}
   :pstate/$$incoming-friend-requests {"bob-id" (sorted-set)
                                       "alice-id" (sorted-set "bob-id")
                                       "hugo-id" (sorted-set "bob-id")
                                       "mael-id" (sorted-set)}
   :pstate/$$friends {"bob-id" (sorted-set)
                      "alice-id" (sorted-set)
                      "hugo-id" (sorted-set "mael-id")
                      "mael-id" (sorted-set "bob-id")}
   :pstate/$$posts {"bob-id" (sorted-map 1 ;;post-id
                                         {:to-user-id "bob-id"
                                          :content "hello from Bob"}
                                         3
                                         {:to-user-id "bob-id"
                                          :content "hello again from Bob"})
                    "alice-id" (sorted-map 2 ;;post-id
                                           {:to-user-id "alice-id"
                                            :content "hello from Alice"})}}
