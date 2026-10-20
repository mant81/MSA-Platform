alter table trace_event add column event_id varchar(36);
alter table trace_event add column span_id varchar(16);
alter table trace_event add column parent_span_id varchar(16);
alter table trace_event add column legacy_trace_id varchar(100);
alter table trace_event add constraint trace_event_identity_unique unique(source_service,event_id);
create index trace_event_trace_idx on trace_event(trace_id,id);
create index trace_event_legacy_idx on trace_event(legacy_trace_id,id);
create index trace_event_process_idx on trace_event(process_id,id);
create index trace_event_error_idx on trace_event(error_code,created_at);
