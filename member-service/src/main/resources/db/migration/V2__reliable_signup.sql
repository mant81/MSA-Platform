alter table member add constraint member_no_unique unique(member_no);
alter table member_process add constraint process_id_unique unique(process_id);
alter table member_process_step add constraint process_step_unique unique(process_id,step_id);
create index member_process_trace_idx on member_process(trace_id);
create index member_process_business_idx on member_process(business_key,id);
create table signup_request (
 request_key char(64) primary key, fingerprint char(64) not null,
 status varchar(20) not null, response_json text, created_at timestamp with time zone not null,
 updated_at timestamp with time zone not null
);
create table trace_outbox (
 event_id varchar(36) primary key, payload text not null, attempts integer not null default 0,
 created_at timestamp with time zone not null, next_attempt_at timestamp with time zone not null,
 delivered_at timestamp with time zone
);
create index trace_outbox_pending_idx on trace_outbox(delivered_at,next_attempt_at);
