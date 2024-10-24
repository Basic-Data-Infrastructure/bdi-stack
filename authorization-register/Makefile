# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: all lint test check clean test-certs jar

test-certs:
	$(MAKE) -C test-config

prep-lint:
	clj -M:lint --lint $$(clojure -Spath)  --copy-configs --dependencies --skip-lint

lint:
	reuse lint
	clojure -M:lint

test: test-certs
	clojure -M:test

clean:
	rm -rf classes target test/pem

bdi-authorization-register.jar: clean
	mkdir classes
	clj -M -e "(compile 'org.bdinetwork.authorization-register.main)"
	clj -M:uberjar --target $@


check: test lint outdated

outdated:
	clojure -M:outdated

copyright-headers:
	reuse annotate \
		--copyright="Jomco B.V."  \
		--copyright="Topsector Logistiek" \
		--license="AGPL-3.0-or-later" \
		--contributor="Joost Diepenmaat <joost@jomco.nl>" \
		--contributor="Remco van 't Veer <remco@jomco.nl>" \
		--fallback-dot-license --recursive .
