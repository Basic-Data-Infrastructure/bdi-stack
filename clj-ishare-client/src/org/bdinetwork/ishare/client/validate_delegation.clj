;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.validate-delegation
  (:require [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as client.request])
  (:import java.time.Instant))


(defn- exact-mismatch?
  [policy-selector policy attribute]
  (and (contains? policy-selector attribute)
       (contains? policy attribute)
       (not= (get policy-selector attribute)
             (get policy attribute))))

(defn- every-mismatch?
  [policy-selector policy attribute]
  (and (contains? policy-selector attribute)
       (contains? policy attribute)
       (not (every? #(contains? (set (get policy-selector attribute)) %)
                    (get policy attribute)))))

(defn- some-mismatch?
  [policy-selector policy attribute]
  (and (contains? policy-selector attribute)
       (contains? policy attribute)
       (not (some #(contains? (set (get policy-selector attribute)) %)
                  (get policy attribute)))))

;; TODO: max delegation-depth
(defn policy-mismatch
  [now policy-selector policy]
  {:pre [(map? policy-selector) (or (nil? policy) (map? policy))]}
  (when-let [issues (if (nil? policy)
                      ["no policy"]
                      (cond-> nil
                        (and (contains? policy :policy/max-delegation-depth)
                             (< 1 (:policy/max-delegation-depth policy)))
                        (conj "max delegation depth exceeded")

                        (not= (:policy/issuer policy-selector) (:policy/issuer policy))
                        (conj "incorrect policy issuer")

                        (and (contains? policy :policy/not-before)
                             (< now (:policy/not-before policy)))
                        (conj "policy not yet valid")

                        (and (contains? policy :policy/not-on-or-after)
                             (< (:policy/not-on-or-after policy) now))
                        (conj "policy expired")

                        (some-mismatch? policy-selector policy :policy/licenses)
                        (conj "no matching license")

                        (exact-mismatch? policy-selector policy :target/access-subject)
                        (conj "invalid access subject")

                        (every-mismatch? policy-selector policy :target/actions)
                        (conj "invalid action")

                        (exact-mismatch? policy-selector policy :resource/type)
                        (conj "invalid resource type")

                        (every-mismatch? policy-selector policy :resource/identifiers)
                        (conj "invalid resource identifier")

                        (every-mismatch? policy-selector policy :resource/attributes)
                        (conj "invalid resource attribute")

                        (every-mismatch? policy-selector policy :environment/service-providers)
                        (conj "invalid service provider")))]
    {:issues          issues
     :policy          policy
     :policy-selector policy-selector}))


(defn policy-chain-mismatch
  "Returns the issues found when validating policy-chain against a policy-selector.

  policy-selector describes the required policies for allowing an action
  on a resource. policy-chain is sequence of actual policies, in order
  of delegation (target/access-subject of a policy is the
  policy/issuer of the next policy). The issuer of the first policy,
  and the access-subject of the last policy must match the issuer and
  access-subject of the policy-selector.

  Missing keys in policy or policy-selector mean no restriction: if policy
  mask does not contain a particular key, any or no value in the chain
  is ok. If a policy does not contain a key, it satisfies any value
  for that key in policy-selector.

  Returns nil if chain is valid according to mask."
  [now policy-selector policy-chain]
  (if (and (contains? policy-selector :policy/max-delegation-depth)
           (> (count policy-chain) (:policy/max-delegation-depth policy-selector)))
    "max delegation depth exceeded"
    (loop [policy-selector          policy-selector
           [policy & rest-policies] policy-chain]
      (or (policy-mismatch now (cond-> policy-selector
                                 (seq rest-policies)
                                 (dissoc :target/access-subject)) policy)
          (if (seq rest-policies)
            (recur (assoc policy-selector :policy/issuer (:target/access-subject policy))
                   rest-policies)
            nil)))))

(defn policy-selector->delegation-mask
  [{:resource/keys    [type identifiers attributes]
    :environment/keys [service-providers]
    :target/keys      [actions] :as policy}]
  {:policyIssuer (:policy/issuer policy)
   :target       {:accessSubject (:target/access-subject policy)}
   :policySets   [(cond-> {:policies [{:target (cond-> {:resource (cond-> {}
                                                                        (some? type)
                                                                        (assoc :type type)

                                                                        (some? identifiers)
                                                                        (assoc :identifiers identifiers)

                                                                        (some? attributes)
                                                                        (assoc :attributes attributes))}
                                                    (seq actions)
                                                    (assoc :actions actions)

                                                    (seq service-providers)
                                                    (assoc-in [:environment :serviceProviders] service-providers))
                                         :rules  [{:effect "Permit"}]}]}

                     (seq (:policy/licenses policy))
                     (assoc-in [:target :environment :licenses] (:policy/licenses policy))
                     
                     (:policy/max-delegation-depth policy)
                     (assoc :maxDelegationDepth (:policy/max-delegation-depth policy)))]})

(defn delegation-evidence->policy
  "Convert an iSHARE delegation-evidence into a policy.

  Returns `nil` if delegation-evidence has a 'Deny' effect or delegation-evidence is nil."
  [{[{[{:keys [rules]} :as policies] :policies} :as policySets] :policySets :as delegation-evidence}]
  (when (some? delegation-evidence)
    
    (when (not= 1 (count policySets))
      (throw (ex-info "Not exactly one policySet" {:policySets policySets})))
    (when (not= 1 (count policies))
      (throw (ex-info "Not exactly one policy" {:policies policies})))
    (when (not= 1 (count rules))
      (throw (ex-info "Not exactly one rule" {:rules rules})))
    (when (= [{:effect "Permit"}] rules)
      (reduce-kv
       (fn [policy k path]
         (if-let [v (get-in delegation-evidence path)]
           (assoc policy k v)
           policy))
       {}
       ;; map of selector key -> delegation mask path
       {:policy/issuer                 [:policyIssuer]
        :policy/max-delegation-depth   [:policySets 0 :maxDelegationDepth]
        :policy/not-before             [:notBefore]
        :policy/not-on-or-after        [:notOnOrAfter]
        :target/access-subject         [:target :accessSubject]
        :policy/licenses               [:policySets 0 :target :environment :licenses]
        :target/actions                [:policySets 0 :policies 0 :target :actions]
        :resource/type                 [:policySets 0 :policies 0 :target :resource :type]
        :resource/identifiers          [:policySets 0 :policies 0 :target :resource :identifiers]
        :resource/attributes           [:policySets 0 :policies 0 :target :resource :attributes]
        :environment/service-providers [:policySets 0 :policies 0 :target :environment :serviceProviders]}))))

(defn- delegation-mask-chain
  "Create delegation masks for fetching delegation evidence chain.

  Given a delegation mask and a sequence of party identifiers,
  starting from the original issuer up until the final access subject,
  return a sequence of delegation masks that can be used to request
  the delegation evidence for the full delegation chain."
  [delegation-mask party-ids]
  (map (fn [[issuer access-subject]]
         (-> delegation-mask
             (assoc "policyIssuer" issuer)
             (assoc-in ["target" "accessSubject"] access-subject)))
       (partition 2 1 party-ids)))

(defn- fetch-delegation-chain
  [base-request delegation-mask party-ids]
  (map (fn [mask]
         (-> base-request
             (client.request/delegation-evidence-request {:delegationRequest mask})
             client/exec
             :ishare/result
             :delegationEvidence))
       (delegation-mask-chain delegation-mask party-ids)))

(defn fetch-and-validate-delegation
  "Fetch and validate delegation evidence for `policy-selector` and `party-ids`.

  Given a `policy-selector` describing the expected authorisations, and
  a sequence of `party-ids`, from original `:policy/issuer` to final
  `:target/access-subject`, fetches the necessary delegation-evidence.

  If the complete chain of delegations can be fetched and it matches
  the expectations in `policy-selector`, returns `nil`.

  Otherwise returns a description of the problems with the delegation
  chain: a map of `:policy-selector` (as given), `:policy` (from the
  offending delegation evidence), and `:issues` (a seq of strings)."
  [base-request policy-selector party-ids]
  (let [now (.getEpochSecond (Instant/now))]
    (->> (fetch-delegation-chain base-request (policy-selector->delegation-mask policy-selector) party-ids)
         (map delegation-evidence->policy)
         (policy-chain-mismatch now policy-selector))))
