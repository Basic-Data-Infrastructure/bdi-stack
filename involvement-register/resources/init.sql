create table involvements (
       principal varchar(250),
       resource_type varchar(250),
       resource varchar(250),
       subject varchar(250),
       role varchar(250)
);


insert into involvements
       (principal, resource_type, resource, subject, role)
       values
       ('dhl', 'bol+cn', '12345+56789', 'schneider', 'shipper');

insert into involvements
       (principal, resource_type, resource, subject, role)
       values
       ('dhl', 'bol+cn', '12346+56790', 'schneider', 'shipper');
