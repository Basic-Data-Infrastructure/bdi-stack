;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.matcher)

(defn match
  "Match `value` with `expr` where `expr` is some subset of `value`, returns false or a map of captured values.

  The shape of `expr` should be available in `value` with non
  collection values being equal unless the `expr` side values are of
  the following types:

  - regular expression

    The `value` should be a string and match the regular expression in
    `expr`.

  - symbols

    Are treated as placeholders and returned in the map of captured
    values.

  Examples:

    (match {:foo \"bar\"} {:foo \"foo\"}) ; => false
    (match {:foo \"bar\"} {:foo \"bar\"}) ; => {}
    (match [{:foo 'foo} 1 #\"huh.*\"] [{:foo \"bar\"} 1 \"huh!\"]) ; => {foo \"bar\"}
"
  [expr value & [vars]]
  (cond
    ;; already captured value should match pre-existing
    (and (symbol? expr) (find vars expr) (not= value (get vars expr)))
    false

    (symbol? expr)
    (assoc vars expr value)

    (= expr value)
    {}

    (and (instance? java.util.regex.Pattern expr)
         (string? value)
         (re-matches expr value))
    {}

    (and (map? expr) (map? value))
    (reduce (fn [vars [k v]]
              (if-let [r (and (find value k)
                              (match v (get value k) vars))]
                (merge vars r)
                (reduced false)))
            {}
            expr)

    (and (coll? expr) (coll? value) (<= (count expr) (count value)))
    (reduce (fn [vars [expr value]]
              (if-let [r (match expr value vars)]
                (merge vars r)
                (reduced false)))
            {}
            (map (fn [& vs] vs) expr value))

    :else
    false))
