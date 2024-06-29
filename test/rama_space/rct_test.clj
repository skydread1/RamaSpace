(ns rama-space.rct-test
  "Rich Comment tests"
  (:require [clojure.test :refer [deftest testing]]
            [com.mjdowney.rich-comment-tests.test-runner :as rctr]))

(deftest rich-comment-tests
  (testing "all white box small tests"
    (rctr/run-tests-in-file-tree! :dirs #{"src"})))
