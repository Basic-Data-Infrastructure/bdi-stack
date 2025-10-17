# SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
# SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: clean test

clean:
	rm -rf ./classes ./target ./bdi-authorization-register.jar

test:
	clojure -A:test -Sdeps '{:deps {lambdaisland/kaocha {:mvn/version "RELEASE"}}}' -M -m kaocha.runner

bdi-authorization-register.jar: clean
	mkdir classes
	clojure -M -e "(compile 'org.bdinetwork.authorization-register.main)"
	clojure -M:uberjar --main-class org.bdinetwork.authorization_register.main --target $@
