alter table service_instance add column maintenance_until timestamp with time zone;
create table alert_outbox (
 event_id varchar(36) primary key, incident_id varchar(36) not null, event_type varchar(20) not null,
 instance_id varchar(100) not null, created_at timestamp with time zone not null,
 next_attempt_at timestamp with time zone not null, attempts integer not null default 0,
 delivered_at timestamp with time zone, unique(incident_id,event_type)
);
create index alert_pending_idx on alert_outbox(delivered_at,next_attempt_at);
