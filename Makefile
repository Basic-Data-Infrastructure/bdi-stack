# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
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

# .fingerprint and .stripped are derived data from certs for inserting
# into test configuration
test/pem/%.fingerprint: test/pem/%.cert.pem
	openssl x509 -in $< -noout -sha256 -fingerprint|sed 's/.*Fingerprint=//' |sed 's/://g' >$@

test/pem/%.cert.stripped: test/pem/%.cert.pem
	cat $< | tr -d '\n' | sed 's/-----BEGIN CERTIFICATE-----//;s/-----END CERTIFICATE-----//' >$@

test/test-config.yml: test/test-config.template.yml test/pem/client.fingerprint test/pem/ca.fingerprint test/pem/client.cert.stripped
	cat $< | \
	  sed "s!{{CA_SUBJECT}}!$(CA_SUBJECT)!" | \
	  sed "s!{{CLIENT_SUBJECT}}!$(CLIENT_SUBJECT)!" | \
	  sed "s!{{SERVER_SUBJECT}}!$(SERVER_SUBJECT)!" | \
	  sed "s!{{CA_FINGERPRINT}}!$(shell cat test/pem/ca.fingerprint)!" | \
	  sed "s!{{CLIENT_FINGERPRINT}}!$(shell cat test/pem/client.fingerprint)!" | \
	  sed "s!{{CLIENT_CERTIFICATE}}!$(shell cat test/pem/client.cert.stripped)!" >$@

lint:
	reuse lint
	clojure -M:lint

test: test-certs test/test-config.yml test/pem/server.fingerprint
	clojure -M:test

clean:
	rm -rf classes target test/pem

bdi-authorization-register.jar: clean
	mkdir classes
	clj -M -e "(compile 'org.bdinetwork.authorization-register.main)"
	clj -M:uberjar --target $@

test-uberjar:
	SERVER_ID=EU.EORI.SERVER PRIVATE_KEY=test/pem/server.key.pem PUBLIC_KEY=test/pem/server.cert.pem X5C=test/pem/server.x5c.pem DATA_SOURCE=test/test-config.yml java -Dlogback.configurationFile=dev/logback.xml -jar bdi-authorization-register.jar


check: test lint outdated

outdated:
	clojure -M:outdated
