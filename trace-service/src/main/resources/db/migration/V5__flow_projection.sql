alter table trace_event add column aggregate_version bigint;
alter table trace_event add column step_execution_id varchar(36);
alter table trace_event add column attempt integer;
alter table trace_event add column process_definition_version varchar(40);
create table flow_projection (
 source_service varchar(100) not null, process_id varchar(100) not null,
 trace_id varchar(100) not null, business_key varchar(100), status varchar(30) not null,
 aggregate_version bigint not null, last_event_id varchar(36), updated_at timestamp with time zone not null,
 primary key(source_service,process_id)
);
create index flow_projection_business_idx on flow_projection(business_key,process_id);
