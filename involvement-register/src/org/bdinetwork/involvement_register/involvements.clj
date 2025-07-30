(ns org.bdinetwork.involvement-register.involvements)

(defn get-involvement-for-subject
  [db {:keys [principal resource_type resource subject] :as selector}]
  {:pre [principal resource_type resource subject]}
  (when-let [involvements (get-in db [{:principal principal
                                       :resource_type resource_type
                                       :resource resource}])]
    (first (filter (= subject (:subject involvements))))))

(defn insert-involvement
  [db])
