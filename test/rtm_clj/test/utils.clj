(ns rtm-clj.test.utils)

(def map-coll
  [{:notes (), :list-id "11413850", :estimate "", :name "Plan exercise for tomorrow (gym / run / cycle)", :postponed "0", :has_due_time "0", :location_id "", :added "2011-09-06T15:30:30Z", :task-series-id "130259984", :url "", :created "2011-09-06T15:30:30Z", :completed "", :modified "2011-09-06T15:30:30Z", :due "2011-09-06T23:00:00Z", :source "android", :id "202456286", :deleted "", :priority "3"},
   {:notes (), :list-id "11413850", :estimate "", :name "Everything that happens to me is the best possible thing that can happen to me ", :postponed "0", :has_due_time "0", :location_id "", :added "2011-08-29T07:46:52Z", :task-series-id "129187773", :url "", :created "2011-08-29T07:46:52Z", :completed "", :modified "2011-09-01T09:53:10Z", :due "2011-09-06T23:00:00Z", :source "android", :id "200586662", :deleted "", :priority "1"}
   {:notes (), :list-id "11413850", :estimate "", :name "Check easy tide", :postponed "0", :has_due_time "0", :location_id "", :added "2011-09-03T06:53:21Z", :task-series-id "129905107", :url "http://easytide.ukho.gov.uk/EasyTide/EasyTide/ShowPrediction.aspx?PortID=0115&PredictionLength=7", :created "2011-09-03T06:53:21Z", :completed "", :modified "2011-09-03T06:53:21Z", :due "2011-09-06T23:00:00Z", :source "js", :id "201806780", :deleted "", :priority "2"}
   {:notes (), :list-id "11413850", :estimate "", :name "Study Clojure", :postponed "0", :has_due_time "0", :location_id "", :added "2011-09-06T18:24:49Z", :task-series-id "130283823", :url "", :created "2011-09-06T18:24:49Z", :completed "", :modified "2011-09-06T18:24:49Z", :due "2011-09-06T23:00:00Z", :source "android", :id "202487544", :deleted "", :priority "2"}
   {:notes (), :list-id "11413850", :estimate "", :name "Check traps", :postponed "0", :has_due_time "0", :location_id "", :added "2011-09-06T17:28:47Z", :task-series-id "130276689", :url "", :created "2011-09-06T17:28:47Z", :completed "", :modified "2011-09-06T17:28:47Z", :due "2011-09-06T23:00:00Z", :source "android", :id "202478891", :deleted "", :priority "3"}])

(def nested-map-coll (map (fn [m] {:id (:list-id m), :name (:name m), :data m}) map-coll))

;; TODO: write a test that can sort the map and nested map by priority (or whatever)
;; Think it should be fairly simple
