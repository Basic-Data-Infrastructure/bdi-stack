# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: all lint test check clean test-certs jar

test/pem/ca.cert.pem:
	mkdir -p test/pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj "/CN=TEST-CA" \
		-keyout test/pem/ca.key.pem \
		-out test/pem/ca.cert.pem

test/pem/aa.cert.pem: test/pem/ca.cert.pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj "/CN=Satellite/serialNumber=EU.EORI.AA" \
		-keyout test/pem/aa.key.pem \
		-out test/pem/aa.cert.pem \
		-CA test/pem/ca.cert.pem \
		-CAkey test/pem/ca.key.pem

test/pem/client.cert.pem: test/pem/ca.cert.pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-keyout test/pem/client.key.pem \
		-out test/pem/client.cert.pem \
		-subj "/CN=Satellite/serialNumber=EU.EORI.CLIENT" \
		-CA test/pem/ca.cert.pem \
		-CAkey test/pem/ca.key.pem

test/pem/client.x5c.pem: test/pem/client.cert.pem test/pem/ca.cert.pem
	cat $^ >$@

test-certs: test/pem/ca.cert.pem test/pem/aa.cert.pem test/pem/client.cert.pem test/pem/client.x5c.pem

lint:
	reuse lint
	clojure -M:lint

test: test-certs
	clojure -M:test

clean:
	rm -rf classes target test/pem

jar: clean
	clj -T:build jar


check: test lint outdated

outdated:
	clojure -M:outdated
