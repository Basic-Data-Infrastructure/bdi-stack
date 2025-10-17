# SPDX-FileCopyrightText: 2024, 2025 Jomco B.V.
# SPDX-FileCopyrightText: 2024, 2025 Stichting Connekt
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: clean test

clean:
	rm -rf ./classes ./target ./bdi-association-register.jar

test:
	clojure -A:test -Sdeps '{:deps {lambdaisland/kaocha {:mvn/version "RELEASE"}}}' -M -m kaocha.runner

bdi-association-register.jar: clean
	mkdir classes
	clojure -M -e "(compile 'org.bdinetwork.association-register.main)"
	clojure -M:uberjar --main-class org.bdinetwork.association_register.main --target $@
