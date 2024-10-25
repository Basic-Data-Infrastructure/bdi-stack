# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

# these parties are defined in the association; we need to generate
# keys and certificates for thme.
PARTIES=authorization_register association_register client nlsmartphon

%.cert.pem %.key.pem : intermediate.cert.pem  intermediate.key.pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj "/CN=$(*)/serialNumber=EU.EORI.$(shell echo $(*) | tr a-z A-Z| tr _ -)" \
		-addext keyUsage=nonRepudiation \
		-keyout "$(*).key.pem" \
		-out "$(*).cert.pem" \
		-CA intermediate.cert.pem \
		-CAkey intermediate.key.pem

certs: $(PARTIES:%=%.key.pem) $(PARTIES:%=%.cert.pem) $(PARTIES:%=%.x5c.pem)

# Root CA -- will be included in the association trusted_list
ca.cert.pem ca.key.pem :
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj "/CN=ca" \
		-addext keyUsage=keyCertSign \
		-keyout ca.key.pem \
		-out ca.cert.pem

# Intermediate CA
intermediate.cert.pem intermediate.key.pem : ca.cert.pem ca.key.pem
	openssl req \
		-x509 -newkey rsa:4096 -sha256 -days 365 -noenc \
		-subj "/CN=intermediate" \
		-addext keyUsage=keyCertSign \
		-keyout intermediate.key.pem \
		-out intermediate.cert.pem \
		-CA ca.cert.pem \
		-CAkey ca.key.pem

# The x5c of a party contains the full certificate path from party to
# root CA. In this case, all parties have the same CA and
# intermediate.
%.x5c.pem: %.cert.pem intermediate.cert.pem ca.cert.pem
	cat $^ >$@

.PHONY: certs

.SECONDARY:



clean:
	rm -f *.pem *.d
