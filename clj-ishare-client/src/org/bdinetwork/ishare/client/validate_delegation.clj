;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
;;; SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
;;; SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
;;;
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.ishare.client.validate-delegation
  (:require [clojure.walk :as walk]
            [org.bdinetwork.ishare.client :as client]
            [org.bdinetwork.ishare.client.request :as ishare.request]
            [org.bdinetwork.noodlebar.request :as noodlebar.request])
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

(defn policy-mismatch
  [now policy-selector policy]
  {:pre [(map? policy-selector) (or (nil? policy) (map? policy))]}
  (when-let [issues (if (nil? policy)
                      ["no policy"]
                      (cond-> nil
                        (and (contains? policy-selector :policy/max-delegation-depth)
                             (< (:policy/max-delegation-depth policy-selector) 1))
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

                        (exact-mismatch? policy-selector policy :policy/access-subject)
                        (conj "invalid access subject")

                        (every-mismatch? policy-selector policy :policy/actions)
                        (conj "invalid action")

                        (exact-mismatch? policy-selector policy :policy/resource-type)
                        (conj "invalid resource type")

                        (every-mismatch? policy-selector policy :policy/resource-identifiers)
                        (conj "invalid resource identifier")

                        (every-mismatch? policy-selector policy :policy/resource-attributes)
                        (conj "invalid resource attribute")

                        (every-mismatch? policy-selector policy :policy/service-providers)
                        (conj "invalid service provider")))]
    {:issues          issues
     :policy          policy
     :policy-selector policy-selector}))


(defn- dec-max-delegation-depth
  [selector-max policy-max]
  (when-let [newmax (if (and selector-max policy-max)
                      (min selector-max policy-max)
                      (or selector-max policy-max))]
    (dec newmax)))

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
           [{max-dd :policy/max-delegation-depth  :as policy} & rest-policies] policy-chain]
      (or (policy-mismatch now (cond-> policy-selector
                                 (seq rest-policies)
                                 (dissoc :policy/access-subject)) policy)
          (if (seq rest-policies)
            (recur (-> policy-selector
                       (assoc :policy/issuer (:policy/access-subject policy))
                       (update :policy/max-delegation-depth dec-max-delegation-depth max-dd))
                   rest-policies)
            nil)))))

(defn policy-selector->delegation-mask
  [{:policy/keys    [resource-type resource-identifiers resource-attributes service-providers actions] :as policy}]
  {:policyIssuer (:policy/issuer policy)
   :target       {:accessSubject (:policy/access-subject policy)}
   :policySets   [(cond-> {:policies [{:target (cond-> {:resource (cond-> {}
                                                                    (some? resource-type)
                                                                    (assoc :type resource-type)

                                                                    (some? resource-identifiers)
                                                                    (assoc :identifiers resource-identifiers)

                                                                    (some? resource-attributes)
                                                                    (assoc :attributes resource-attributes))}
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

    ;; The iSHARE specs allow for complex delegation evidence, with
    ;; multiple policysets, policies and rules, but we cannot convert
    ;; those correctly into a single policy, and using incorrectly
    ;; converted policies may cause security issues.
    ;;
    ;; We use `throw`s instead of `assert`s, to ensure the checks
    ;; cannot be disabled.

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
       {:policy/issuer               [:policyIssuer]
        :policy/max-delegation-depth [:policySets 0 :maxDelegationDepth]
        :policy/not-before           [:notBefore]
        :policy/not-on-or-after      [:notOnOrAfter]
        :policy/access-subject       [:target :accessSubject]
        :policy/licenses             [:policySets 0 :target :environment :licenses]
        :policy/actions              [:policySets 0 :policies 0 :target :actions]
        :policy/resource-type        [:policySets 0 :policies 0 :target :resource :type]
        :policy/resource-identifiers [:policySets 0 :policies 0 :target :resource :identifiers]
        :policy/resource-attributes  [:policySets 0 :policies 0 :target :resource :attributes]
        :policy/service-providers    [:policySets 0 :policies 0 :target :environment :serviceProviders]}))))

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
             (ishare.request/delegation-evidence-request {:delegationRequest mask})
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

(defn noodlebar-fetch-delegation-evidence
  [base-request delegation-mask]
  (-> base-request
      (noodlebar.request/unsigned-delegation-request delegation-mask)
      client/exec
      :body
      (walk/keywordize-keys)))

(defn delegation-mask-evidence-mismatch
  [mask evidence]
  (policy-mismatch (.getEpochSecond (Instant/now))
                   (delegation-evidence->policy mask)
                   (delegation-evidence->policy evidence)))
