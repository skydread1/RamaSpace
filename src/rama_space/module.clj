(ns rama-space.module
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.rama.path :as path]
            [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.ops :as ops]
            [rama-space.pstate-sch :as pstate])
  (:import [com.rpl.rama.helpers ModuleUniqueIdPState]))

;;---------- app module  ----------

(defn current-time
  []
  (System/currentTimeMillis))

;; * variables are values
;; % variables are anonymous operations
;; $$ variables are PStates
(defmodule RamaSpaceModule [setup topologies]
  (let [users-stream (stream-topology topologies "users")
        friends-stream (stream-topology topologies "friends")
        posts-microbatch (microbatch-topology topologies "posts")
        ;; small utility from rama-helpers for generating unique 64-bit IDs.
        ;; It works by declaring a PState tracking a counter on each task and combining that counter with the task ID when generating an ID.
        module-unique-id-gen (ModuleUniqueIdPState. "$$post-id")
        profile-views-microbatch (microbatch-topology topologies "profile-views")]
    (declare-depot setup *user-registrations-depot (hash-by :user-id))
    (declare-depot setup *profile-edits-depot (hash-by :user-id))
    (declare-depot setup *friend-requests-depot (hash-by :user-id))
    (declare-depot setup *friendship-depot (hash-by :user-id))
    (declare-depot setup *posts-depot (hash-by :to-user-id))
    (declare-depot setup *profile-views-depot (hash-by :to-user-id))
    ;; The .declarePState call declares the PState it uses.
    (.declarePState module-unique-id-gen posts-microbatch)

    (declare-pstate users-stream $$profiles pstate/profiles-sch)
    (declare-pstate friends-stream $$incoming-friend-requests pstate/incoming-friend-requests-sch)
    (declare-pstate friends-stream $$outgoing-friend-requests pstate/outgoing-friend-requests-sch)
    (declare-pstate friends-stream $$friends pstate/friends-sch)
    (declare-pstate posts-microbatch $$posts pstate/posts-sch)
    (declare-pstate profile-views-microbatch $$profile-views pstate/profile-views-sch)

    (<<sources users-stream
               ;;==========  registration
               (source> *user-registrations-depot :> {:keys [*user-id
                                                             *display-name
                                                             *email
                                                             *profile-pic
                                                             *bio
                                                             *location
                                                             *pwd-hash
                                                             *registration-uuid]})
               (current-time :> *joined-at-millis)
               (local-transform> [(path/keypath *user-id)
                                  (path/pred nil?)
                                  (path/multi-path [:display-name (path/termval *display-name)]
                                                   [:email (path/termval *email)]
                                                   [:profile-pic (path/termval *profile-pic)]
                                                   [:location (path/termval *location)]
                                                   [:bio (path/termval *bio)]
                                                   [:pwd-hash (path/termval *pwd-hash)]
                                                   [:joined-at-millis (path/termval *joined-at-millis)]
                                                   [:registration-uuid (path/termval *registration-uuid)])]
                                 $$profiles)
               ;;==========  profile edit
               (source> *profile-edits-depot :> {:keys [*userId *field *value]})
               (local-transform> [(path/keypath *userId *field)
                                  (path/termval *value)]
                                 $$profiles))
    (<<sources friends-stream
               ;;==========  make/cancel friend requests
               (source> *friend-requests-depot :> {:keys [*action *user-id *to-user-id]})
               (<<switch *action
                         (case> :request)
                         (|hash *user-id)
                         (+compound $$outgoing-friend-requests
                                    {*user-id (aggs/+set-agg *to-user-id)})
                         (|hash *to-user-id)
                         (+compound $$incoming-friend-requests
                                    {*to-user-id (aggs/+set-agg *user-id)})

                         (case> :cancel)
                         (|hash *user-id)
                         (+compound $$outgoing-friend-requests
                                    {*user-id (aggs/+set-remove-agg *to-user-id)})
                         (|hash *to-user-id)
                         (+compound $$incoming-friend-requests
                                    {*to-user-id (aggs/+set-remove-agg *user-id)}))
               ;;========== add/remove friends
               (source> *friendship-depot :> {:keys [*action *user-id-1 *user-id-2]})
               (anchor> <change-friendship>) ;; create new branch
               ;;---------- clear incoming/outgoing friend requests
               (|hash *user-id-1)
               (+compound $$incoming-friend-requests
                          {*user-id-1 (aggs/+set-remove-agg *user-id-2)})
               (+compound $$outgoing-friend-requests
                          {*user-id-1 (aggs/+set-remove-agg *user-id-2)})
               (|hash *user-id-2)
               (+compound $$incoming-friend-requests
                          {*user-id-2 (aggs/+set-remove-agg *user-id-1)})
               (+compound $$outgoing-friend-requests
                          {*user-id-2 (aggs/+set-remove-agg *user-id-1)})
               (hook> <change-friendship>) ;; checkout branch
               ;;---------- add/remove friendships
               (<<switch *action
                         (case> :add)
                         (|hash *user-id-1)
                         (+compound $$friends
                                    {*user-id-1 (aggs/+set-agg *user-id-2)})
                         (|hash *user-id-2)
                         (+compound $$friends
                                    {*user-id-2 (aggs/+set-agg *user-id-1)})

                         (case> :remove)
                         (|hash *user-id-1)
                         (+compound $$friends
                                    {*user-id-1 (aggs/+set-remove-agg *user-id-2)})
                         (|hash *user-id-2)
                         (+compound $$friends
                                    {*user-id-2 (aggs/+set-remove-agg *user-id-1)})))

    (<<sources posts-microbatch
               ;;========== add posts
               ;; Calling %microbatch causes all the posts for the iteration to be emitted across all partitions individually
               (source> *posts-depot :> %microbatch)
               (%microbatch :> {:keys [*to-user-id] :as *post})
               (java-macro! (.genId module-unique-id-gen "*post-id")) ;; generate a unique post-id accross the entire module
               (local-transform> [(path/keypath *to-user-id *post-id)
                                  (path/termval *post)]
                                 $$posts))

    (<<sources profile-views-microbatch
               ;;========== add profile view count to hour-bucket
               (source> *profile-views-depot :> %microbatch)
               (%microbatch :> {:keys [*to-user-id *timestamp]})
               (quot *timestamp (* 1000 60 60) :> *bucket)
               (+compound $$profile-views
                          {*to-user-id {*bucket (aggs/+count)}}))

    (<<query-topology topologies "posts-of-user"
                      [*for-user-id *start-post-id :> *result-map]
                      (|hash *for-user-id)
                      (local-select> [(path/keypath *for-user-id)
                                      (path/sorted-map-range-from *start-post-id 20)]
                                     $$posts :> *post)
                      (ops/explode-map *post :> *post-id {:keys [*to-user-id *content]})
                      (|hash *to-user-id)
                      (local-select> [(path/keypath *to-user-id :display-name)]
                                     $$profiles :> *display-name)
                      (local-select> [(path/keypath *to-user-id :profile-pic)]
                                     $$profiles :> *profile-pic)
                      (identity {:user-id *to-user-id
                                 :content *content
                                 :display-name *display-name
                                 :profile-pic *profile-pic} :> *resolved-post)
                      (|origin)
                      (+compound {*post-id (aggs/+last *resolved-post)} :> *result-map))))
