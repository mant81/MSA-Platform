create table flow_step_projection (
 source_service varchar(100) not null,
 process_id varchar(100) not null,
 step_execution_id varchar(36) not null,
 step_id varchar(100) not null,
 attempt integer not null check (attempt > 0),
 trace_id varchar(100) not null,
 span_id varchar(16),
 status varchar(30) not null,
 aggregate_version bigint not null,
 last_event_id varchar(36),
 updated_at timestamp with time zone not null,
 primary key (source_service, process_id, step_execution_id)
);
create index idx_flow_step_process on flow_step_projection(process_id,step_id,attempt);
insert into flow_step_projection(source_service,process_id,step_execution_id,step_id,attempt,
 trace_id,span_id,status,aggregate_version,last_event_id,updated_at)
select source_service,process_id,step_execution_id,step_id,attempt,trace_id,span_id,
 case event_type when 'STEP_SUCCEEDED' then 'SUCCEEDED' when 'STEP_NOT_EXECUTED' then 'NOT_EXECUTED'
 when 'STEP_FAILED' then case when status='TIMEOUT' then 'TIMED_OUT' else 'FAILED' end
 else 'RUNNING' end,aggregate_version,event_id,coalesce(created_at,current_timestamp)
from (select t.*,row_number() over(partition by source_service,process_id,step_execution_id
 order by case when event_type='STEP_STARTED' then 1 else 0 end,aggregate_version desc,id desc) as rn
 from trace_event t where source_service='member-service' and process_id is not null
 and step_execution_id is not null and step_id is not null and attempt>0
 and aggregate_version is not null
 and event_type in ('STEP_STARTED','STEP_SUCCEEDED','STEP_FAILED','STEP_NOT_EXECUTED')) ranked
where rn=1;
