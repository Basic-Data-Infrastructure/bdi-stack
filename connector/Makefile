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
