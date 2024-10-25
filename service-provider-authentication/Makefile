# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: all lint test check clean test-certs jar

CA_SUBJECT="/CN=TEST-CA"
SERVER_SUBJECT="/CN=Satellite/serialNumber=EU.EORI.SERVER"
CLIENT_SUBJECT="/CN=Satellite/serialNumber=EU.EORI.CLIENT"

test/pem/ca.cert.pem:
	mkdir -p test/pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj $(CA_SUBJECT) \
		-addext keyUsage=keyCertSign \
		-keyout test/pem/ca.key.pem \
		-out test/pem/ca.cert.pem

test/pem/intermediate.cert.pem:
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj "/CN=INTERMEDIATE-CA" \
		-addext keyUsage=keyCertSign \
		-keyout test/pem/intermediate.key.pem \
		-out test/pem/intermediate.cert.pem \
		-CA test/pem/ca.cert.pem \
		-CAkey test/pem/ca.key.pem

test/pem/server.cert.pem: test/pem/intermediate.cert.pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj $(SERVER_SUBJECT) \
		-addext keyUsage=nonRepudiation \
		-keyout test/pem/server.key.pem \
		-out test/pem/server.cert.pem \
		-CA test/pem/intermediate.cert.pem \
		-CAkey test/pem/intermediate.key.pem

test/pem/client.cert.pem: test/pem/ca.cert.pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-keyout test/pem/client.key.pem \
		-out test/pem/client.cert.pem \
		-addext keyUsage=nonRepudiation \
		-subj $(CLIENT_SUBJECT) \
		-CA test/pem/intermediate.cert.pem \
		-CAkey test/pem/intermediate.key.pem

test/pem/%.x5c.pem: test/pem/%.cert.pem test/pem/intermediate.cert.pem test/pem/ca.cert.pem
	cat $^ >$@

test-certs: test/pem/ca.cert.pem test/pem/server.cert.pem test/pem/client.cert.pem test/pem/client.x5c.pem test/pem/server.x5c.pem

lint:
	reuse lint
	clojure -M:lint

test: test-certs
	clojure -M:test

clean:
	rm -rf classes target test/pem

check: test lint outdated

outdated:
	clojure -M:outdated
