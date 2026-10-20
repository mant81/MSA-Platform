alter table trace_event add column error_id varchar(36);
alter table trace_event add column caused_by_error_id varchar(36);
alter table trace_event add column error_group_id varchar(150);
create index trace_error_identity_idx on trace_event(error_id);
create index trace_error_cause_idx on trace_event(caused_by_error_id);
