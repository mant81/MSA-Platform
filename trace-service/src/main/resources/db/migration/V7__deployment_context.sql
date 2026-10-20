alter table trace_event add column environment varchar(40);
alter table trace_event add column instance_id varchar(100);
alter table trace_event add column deployment_version varchar(100);
create index trace_business_key_idx on trace_event(business_key,process_id);
create index trace_created_idx on trace_event(created_at,id);
