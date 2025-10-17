;;; SPDX-FileCopyrightText: 2025 Jomco B.V.
;;; SPDX-FileCopyrightText: 2025 Stichting Connekt
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
        (throw (ex-info (str "lookup failed: " (pr-str expr))
                        {:expr expr})))
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
        (try
          (apply (evaluate oper env)
                 (map #(evaluate % env) args))
          (catch Exception e
            (throw (ex-info (str "failed to execute expression: " (pr-str expr))
                            {:oper oper, :args args, :expr expr}
                            e))))))

    :else
    (throw (ex-info (str "unexpected expression: " (pr-str expr))
                    {:expr expr}))))
