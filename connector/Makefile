# SPDX-FileCopyrightText: 2025 Jomco B.V.
# SPDX-FileCopyrightText: 2025 Stichting Connekt
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: clean test

clean:
	rm -rf ./classes ./target ./bdi-connector.jar

test:
	clojure -A:test -Sdeps '{:deps {lambdaisland/kaocha {:mvn/version "RELEASE"}}}' -M -m kaocha.runner

bdi-connector.jar: clean
	mkdir classes
	clojure -M -e "(compile 'org.bdinetwork.connector.main)"
	clojure -M:uberjar --main-class org.bdinetwork.connector.main --target $@

interceptors.md: deps.edn src/org/bdinetwork/connector/interceptors.clj src/org/bdinetwork/connector/interceptors/*.clj
	clojure -X:print-interceptors > interceptors.md

README.md: interceptors.md README.src.md
	echo "<!-- WARNING! THIS FILE IS GENERATED, EDIT README.src.md INSTEAD -->" >$@
	sed "/<!-- INCLUDE interceptors.md -->/r $<" README.src.md >>$@
