# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: clean

clean:
	rm -rf ./classes ./target ./bdi-association-register.jar

bdi-association-register.jar: clean
	mkdir classes
	clojure -M -e "(compile 'org.bdinetwork.association-register.main)"
	clojure -M:uberjar --main-class org.bdinetwork.association_register.main --target $@
