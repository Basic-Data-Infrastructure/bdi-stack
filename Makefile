# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: lint test check clean test-config jar

test-config:
	$(MAKE) -C test-config

prep-lint:
	clojure -M:lint --lint $$(clojure -Spath)  --copy-configs --dependencies --skip-lint

lint: prep-lint
	clojure -M:lint --lint */src */test
	reuse lint

test: test-config
	clojure -M:test

clean:
	rm -rf ./*/classes ./*/target test-config/*pem ./*/*.jar ./*.zip

check: test lint outdated

outdated:
	clojure -T:antq outdated

copyright-headers:
	reuse annotate \
		--copyright="Jomco B.V."  \
		--copyright="Topsector Logistiek" \
		--license="AGPL-3.0-or-later" \
		--contributor="Joost Diepenmaat <joost@jomco.nl>" \
		--contributor="Remco van 't Veer <remco@jomco.nl>" \
		--style=lisp \
		--skip-existing \
		./*/*.edn ./*.edn

	reuse annotate \
		--copyright="Jomco B.V."  \
		--copyright="Topsector Logistiek" \
		--license="AGPL-3.0-or-later" \
		--contributor="Joost Diepenmaat <joost@jomco.nl>" \
		--contributor="Remco van 't Veer <remco@jomco.nl>" \
		--skip-unrecognised \
		--skip-existing \
		--recursive .

watson:
	clojure -M:watson scan -p deps.edn -f -s

%.jar:
	$(MAKE) -C $(dir $@) $(notdir $@)

bdi-association-register-%.zip: association-register/bdi-association-register.jar association-register/README.md LICENSES
	rm -rf "$(basename $@)"
	mkdir -p "$(basename $@)"
	cp -r $^ "$(basename $@)/"
	zip -r $@ "$(basename $@)"

bdi-authorization-register-%.zip: authorization-register/bdi-authorization-register.jar authorization-register/README.md LICENSES
	rm -rf "$(basename $@)"
	mkdir -p "$(basename $@)"
	cp -r $^ "$(basename $@)/"
	zip -r $@ "$(basename $@)"
