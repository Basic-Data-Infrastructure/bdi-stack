;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Topsector Logistiek
;;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns org.bdinetwork.gateway.eval)

(defn evaluate
  "Evaluate expression.

  The `expr` is an EDN object.  Supported constructs are:

  - symbols (interpreted as vars from `env`)

  - booleans, number, strings and keywords

  - lists (interpreted as a form, only `if`, `and` and `or` are
    recognized as special forms)

  - vectors and maps"
  [expr env]
  (cond
    (or (boolean? expr) (number? expr) (string? expr) (keyword? expr))
    expr

    (symbol? expr)
    (let [v (get env expr ::lookup-failed)]
      (when (= ::lookup-failed v)
        (throw (ex-info "lookup failed" {:expr expr})))
      v)

    (vector? expr)
    (mapv #(evaluate % env) expr)

    (map? expr)
    (->> expr
         (map (fn [[k v]] [(evaluate k env)
                           (evaluate v env)]))
         (into {}))

    (seq? expr)
    (let [[oper & args] expr]
      (condp = oper
        'if
        (let [[test effect alternative] args]
          (if (evaluate test env)
            (evaluate effect env)
            (evaluate alternative env)))

        'and
        (every? identity (map #(evaluate % env) args))

        'or
        (boolean (some #(evaluate % env) args))

        ;; else
        (apply (evaluate oper env)
               (map #(evaluate % env) args))))

    :else
    (throw (ex-info "unexpected expression" {:expr expr}))))

(defn substitute-symbols
  "Place symbols in `v` by values in `vars`.
  If symbol is not in `vars` it is replaced by `:UNDEFINED`."
  [vars v]
  (cond
    (symbol? v) (get vars v :UNDEFINED)

    (coll? v) (into (if (map-entry? v) [] (empty v))
                    (mapv (partial substitute-symbols vars) v))

    :else v))
