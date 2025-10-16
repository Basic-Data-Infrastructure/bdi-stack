-- SPDX-FileCopyrightText: 2025 Jomco B.V.
-- SPDX-FileCopyrightText: 2025 Topsector Logistiek
-- SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
-- SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
--
-- SPDX-License-Identifier: AGPL-3.0-or-later

create table policy (
       id uuid not null primary key,
       issuer varchar(256) not null,
       not_before timestamp with time zone,
       not_on_or_after timestamp with time zone,
       max_delegation_depth int,
       licenses text[],
       access_subject text not null,
       actions text[],
       resource_type text,
       resource_identifiers text[],
       resource_attributes text[],
       service_providers text[]
);
