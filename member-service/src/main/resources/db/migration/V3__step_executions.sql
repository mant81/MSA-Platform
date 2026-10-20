alter table member_process_step add column step_execution_id varchar(36);
alter table member_process_step add column attempt integer not null default 1 check(attempt > 0);
create unique index member_step_execution_unique on member_process_step(step_execution_id);
