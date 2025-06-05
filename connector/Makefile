# SPDX-FileCopyrightText: 2025 Jomco B.V.
# SPDX-FileCopyrightText: 2025 Topsector Logistiek
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: clean

clean:
	rm -rf ./classes ./target ./bdi-connector.jar

bdi-connector.jar: clean
	mkdir classes
	clojure -M -e "(compile 'org.bdinetwork.connector.main)"
	clojure -M:uberjar --main-class org.bdinetwork.connector.main --target $@
