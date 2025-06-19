# SPDX-FileCopyrightText: 2025 Jomco B.V.
# SPDX-FileCopyrightText: 2025 Topsector Logistiek
#
# SPDX-License-Identifier: AGPL-3.0-or-later

.PHONY: test

test:
	clojure -A:test -Sdeps '{:deps {lambdaisland/kaocha {:mvn/version "RELEASE"}}}' -M -m kaocha.runner
