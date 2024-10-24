<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Authorization Register

An implementation of a standalone Authorization Register (AR), as
defined by the Basic Data Infrastructure Framework. This AR implements
a the iSHARE Authorization Register API, plus an API for managing
authorization policies; delegation evidence is provided based on the
policies present.

Policies are kept in an in-memory database (durability of policies is
work-in-progress), The AR provides a PolicyView protocol for quering
policies and a PolicyStore protocol for adding and deleting policies.

## ⚠ DISCLAIMER ⚠

**The software is for demo purposes only!**  It has not been audited
for security flaws and is not suitable as a starting point to develop
software.  Use at your own risk.

## Copying

Copyright (C) 2024 Jomco B.V.

Copyright (C) 2024 Topsector Logistiek

[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
