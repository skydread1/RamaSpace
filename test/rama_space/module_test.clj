(ns rama-space.module-test
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [clojure.test :refer [deftest testing is]]
            [com.rpl.rama.path :as path]
            [com.rpl.rama.test :as rtest]
            [rama-space.module :as sut]))

(deftest module-test
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc sut/RamaSpaceModule {:tasks 4 :threads 2})
    (let [module-name (get-module-name sut/RamaSpaceModule)
          registrations-depot (foreign-depot ipc module-name "*user-registrations-depot")
          profile-edits-depot (foreign-depot ipc module-name "*profile-edits-depot")
          friend-requests-depot (foreign-depot ipc module-name "*friend-requests-depot")
          friendship-depot (foreign-depot ipc module-name "*friendship-depot")
          posts-depot (foreign-depot ipc module-name "*posts-depot")
          profile-views-depot (foreign-depot ipc module-name "*profile-views-depot")

          profiles-pstate (foreign-pstate ipc module-name "$$profiles")
          outgoing-friends-pstate (foreign-pstate ipc module-name "$$outgoing-friend-requests")
          incoming-friends-pstate (foreign-pstate ipc module-name "$$incoming-friend-requests")
          friends-pstate (foreign-pstate ipc module-name "$$friends")
          posts-pstate (foreign-pstate ipc module-name "$$posts")
          profile-views-pstate (foreign-pstate ipc module-name "$$profile-views")

          posts-of-user-query (foreign-query ipc module-name "posts-of-user")]
      (testing "Registration"
        (foreign-append! registrations-depot {:user-id "bob-id"
                                              :display-name "bob"
                                              :email "bob@mail.sg"
                                              :profile-pic "pic-of-bob"
                                              :location "Singapore"
                                              :bio "bio of bob"
                                              :pwd-hash (hash "user-id")
                                              :joined-at-millis (System/currentTimeMillis)
                                              :registration-uuid (str (random-uuid))})
        (is (= {:display-name "bob"
                :email "bob@mail.sg"}
               (foreign-select-one [(path/keypath "bob-id")
                                    (path/submap [:display-name :email])]
                                   profiles-pstate)))
        (is (> (System/currentTimeMillis)
               (foreign-select-one [(path/keypath "bob-id" :joined-at-millis)]
                                   profiles-pstate))))
      (testing "Profile field update"
        (foreign-append! profile-edits-depot {:user-id "bob-id"
                                              :field :email
                                              :value "bob-2@mail.sg"})
        (is (= "bob@mail.sg"
               (foreign-select-one [(path/keypath "bob-id" :email)]
                                   profiles-pstate))))
      (testing "Outgoing/Incoming friend request"
        (testing "Make friend request"
          (foreign-append! friend-requests-depot
                           {:action :request
                            :user-id "bob-id"
                            :to-user-id "alice-id"})
          (is (= #{"alice-id"}
                 (foreign-select-one [(path/keypath "bob-id")
                                      (path/sorted-set-range-from "" {:max-amt 2
                                                                      :inclusive? false})]
                                     outgoing-friends-pstate)))
          (is (= #{"bob-id"}
                 (foreign-select-one [(path/keypath "alice-id")
                                      (path/sorted-set-range-from "" {:max-amt 2
                                                                      :inclusive? false})]
                                     incoming-friends-pstate))))
        (testing "Cancel friend request"
          (foreign-append! friend-requests-depot
                           {:action :cancel
                            :user-id "bob-id"
                            :to-user-id "alice-id"})
          (is (= #{}
                 (foreign-select-one [(path/keypath "bob-id")
                                      (path/sorted-set-range-from "" {:max-amt 2
                                                                      :inclusive? false})]
                                     outgoing-friends-pstate)
                 (foreign-select-one [(path/keypath "alice-id")
                                      (path/sorted-set-range-from "" {:max-amt 2
                                                                      :inclusive? false})]
                                     incoming-friends-pstate))))
        (testing "Alice accepts Bob's friend request"
          (foreign-append! friend-requests-depot
                           {:action :request
                            :user-id "bob-id"
                            :to-user-id "alice-id"})
          (foreign-append! friendship-depot
                           {:action :add
                            :user-id-1 "bob-id"
                            :user-id-2 "alice-id"})
          (testing "Alice and Bob are friends"
            (is (= #{"alice-id"}
                   (foreign-select-one [(path/keypath "bob-id")
                                        (path/sorted-set-range-from "" {:max-amt 2
                                                                        :inclusive? false})]
                                       friends-pstate)))
            (is (= #{"bob-id"}
                   (foreign-select-one [(path/keypath "alice-id")
                                        (path/sorted-set-range-from "" {:max-amt 2
                                                                        :inclusive? false})]
                                       friends-pstate))))
          (testing "Bob's friend request has been cleared"
            (is (= #{}
                   (foreign-select-one [(path/keypath "bob-id")
                                        (path/sorted-set-range-from "" {:max-amt 2
                                                                        :inclusive? false})]
                                       outgoing-friends-pstate)
                   (foreign-select-one [(path/keypath "alice-id")
                                        (path/sorted-set-range-from "" {:max-amt 2
                                                                        :inclusive? false})]
                                       incoming-friends-pstate)))))
        (testing "Add posts"
          (foreign-append! posts-depot
                           {:to-user-id "bob-id"
                            :content "Rise and shine, Mister Freeman."})
          (foreign-append! posts-depot
                           {:to-user-id "alice-id"
                            :content "Cake and grief counseling will be available at the conclusion of the test."})
          (foreign-append! posts-depot
                           {:to-user-id "bob-id"
                            :content "Time, Dr. Freeman? Is it really that time again? It seems as if you only just arrived."})
          (rtest/wait-for-microbatch-processed-count ipc module-name "posts" 3)
          (is (= [{:to-user-id "bob-id"
                   :content "Rise and shine, Mister Freeman."}
                  {:to-user-id "bob-id"
                   :content "Time, Dr. Freeman? Is it really that time again? It seems as if you only just arrived."}]
                 (foreign-select [(path/keypath "bob-id")
                                  path/MAP-VALS]
                                 posts-pstate)))
          (is (= [{:to-user-id "alice-id"
                   :content "Cake and grief counseling will be available at the conclusion of the test."}]
                 (foreign-select [(path/keypath "alice-id")
                                  path/MAP-VALS]
                                 posts-pstate))))
        (testing "Add profile view counts"
          (let [start-hour-bucket (quot (System/currentTimeMillis) (* 1000 60 60))]
            (foreign-append! profile-views-depot
                             {:to-user-id "bob-id"
                              :timestamp (System/currentTimeMillis)})
            (foreign-append! profile-views-depot
                             {:to-user-id "alice-id"
                              :timestamp (System/currentTimeMillis)})
            (foreign-append! profile-views-depot
                             {:to-user-id "bob-id"
                              :timestamp (System/currentTimeMillis)})
            (rtest/wait-for-microbatch-processed-count ipc module-name "profile-views" 3)
            (let [path-of (fn [user-id] [(path/keypath user-id)
                                         (path/sorted-map-range start-hour-bucket (inc start-hour-bucket))
                                         (path/subselect path/MAP-VALS)
                                         (path/view #(reduce + %))])]
              (is (= 2
                     (foreign-select-one (path-of "bob-id") profile-views-pstate)))
              (is (= 1
                     (foreign-select-one (path-of "alice-id") profile-views-pstate))))))
        (testing "Feed of Bob"
          (is (= [{:content "Rise and shine, Mister Freeman."
                   :profile-pic "pic-of-bob"
                   :user-id "bob-id"
                   :display-name "bob"}
                  {:content
                   "Time, Dr. Freeman? Is it really that time again? It seems as if you only just arrived.",
                   :profile-pic "pic-of-bob"
                   :user-id "bob-id"
                   :display-name "bob"}]
                 (vals
                  (foreign-invoke-query posts-of-user-query "bob-id" 0)))))))))
