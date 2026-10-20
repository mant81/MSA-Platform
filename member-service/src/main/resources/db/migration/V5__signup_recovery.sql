alter table signup_request add column owner_id varchar(100);
alter table signup_request add column member_no varchar(50);
alter table signup_request add column initial_trace_id varchar(100);
alter table signup_request add column retry_count integer not null default 0 check(retry_count >= 0);
create index signup_recovery_status_idx on signup_request(status,request_key);
create table signup_recovery_audit (
 event_id varchar(36) primary key, request_key char(64) not null references signup_request(request_key),
 actor varchar(100) not null, action varchar(30) not null, reason_code varchar(40) not null,
 trace_id varchar(100) not null, created_at timestamp with time zone not null
);
create index signup_recovery_audit_request_idx on signup_recovery_audit(request_key,created_at);
