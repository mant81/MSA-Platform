"use strict";
let token = null;
let cursor = 0;
const el = id => document.getElementById(id);
const text = (tag, value) => { const node = document.createElement(tag);node.textContent = value ?? '—';return node; };
const badge = state => { const node=text('span',state);node.className='badge';if(/^[A-Z_]+$/.test(state || ''))node.classList.add(state);return node; };
const normalize = row => Object.fromEntries(Object.entries(row).map(([k,v])=>[k.toLowerCase(),v]));
async function api(path, options={}) {
 const response=await fetch('/api'+path,{...options,headers:{...(token?{Authorization:'Bearer '+token}:{}),'Content-Type':'application/json',...options.headers},cache:'no-store'});
 const body=response.status===204?{}:await response.json();
 if(!response.ok)throw Error(response.status===401?'로그인이 필요합니다.':response.status===403?'운영 조회 권한이 없습니다.':'요청을 처리하지 못했습니다 ('+response.status+').');
 return body;
}
async function safely(work){el('notice').textContent='';try{await work();}catch(error){el('notice').textContent=error.message;}}
el('login').addEventListener('submit',event=>{event.preventDefault();safely(async()=>{
 token=null;const body=await api('/auth/login',{method:'POST',body:JSON.stringify({userId:el('operator').value,password:el('password').value,authType:'PASSWORD'})});
 token=body.accessToken;el('password').value='';el('session').textContent='로그인했습니다. 토큰은 현재 화면의 메모리에만 보관합니다.';await refresh();
});});
el('logout').addEventListener('click',()=>{token=null;el('session').textContent='로그아웃했습니다.';['services','instances','incidents','errors','impact','timeline','projection','delivery','observed','completeness'].forEach(id=>el(id).replaceChildren());});
async function refresh(){
 const status=await api('/operations/services');el('observed').textContent='기준 시각 '+status.observedAt+' · 수집기 '+status.collectorState;
 el('services').replaceChildren(...Object.entries(status.services).map(([name,state])=>{const card=text('div','');card.className='card';card.append(text('strong',name),text('br',''),badge(state));return card;}));
 el('instances').replaceChildren(...status.instances.map(item=>{const row=document.createElement('tr');const state=document.createElement('td');state.append(badge(item.state));row.append(text('td',item.service_name+' / '+item.instance_id),state,text('td',item.last_checked),text('td',item.reason));return row;}));
 const [errors,incidents,delivery]=await Promise.all([api('/operations/errors'),api('/operations/incidents'),api('/operations/alerts')]);
 el('delivery').textContent=delivery.configured?'알림 전송 대기 '+delivery.pending+'건':'알림 수신처 미설정 · 전송 대기 '+delivery.pending+'건';
 el('errors').replaceChildren(...errors.map(item=>{const row=text('div','');row.className='row';const button=text('button','영향 업무 보기');button.addEventListener('click',()=>safely(()=>impact(item.error_code)));row.append(text('strong',item.error_code),text('span',item.source_service+' · '+item.occurrences+'회 · 영향 업무 '+item.affected_processes),button);return row;}));
 el('incidents').replaceChildren(...incidents.map(normalize).map(item=>{const row=text('div','');row.className='row';row.append(badge(item.state),text('span',item.instance_id+' · '+item.opened_at));if(item.state==='OPEN'&&!item.acknowledged_at){const button=text('button','장애 확인');button.addEventListener('click',()=>safely(async()=>{await api('/operations/incidents/'+encodeURIComponent(item.incident_id)+'/ack',{method:'POST'});await refresh();}));row.append(button);}else if(item.acknowledged_by)row.append(text('span','확인: '+item.acknowledged_by));return row;}));
}
async function impact(code){const rows=await api('/operations/errors/'+encodeURIComponent(code)+'/impact');el('impact').replaceChildren(...rows.map(item=>{const row=text('div','');row.className='row';row.append(text('span',item.error_code),text('code',item.error_id),text('span',item.process_id||'업무 연결 없음'));if(item.process_id){const button=text('button','흐름 보기');button.addEventListener('click',()=>safely(async()=>{el('process').value=item.process_id;cursor=0;await flow();}));row.append(button);}return row;}));}
async function flow(){const page=await api('/operations/flows?processId='+encodeURIComponent(el('process').value)+'&cursor='+cursor);const maximum=Math.max(1,...page.events.map(item=>item.elapsed_ms||0));
 const rows=page.events.map(item=>{const row=document.createElement('tr');const state=document.createElement('td');state.append(badge(item.status||'UNKNOWN'));const duration=text('td',(item.elapsed_ms??0)+' ms');const bar=text('div','');bar.className='bar';bar.style.width=Math.min(100,100*(item.elapsed_ms||0)/maximum)+'%';duration.append(bar);const link=text('td','');link.append(text('code','Trace '+item.trace_id),text('br',''),text('code','Span '+(item.span_id||'—')+' ← '+(item.parent_span_id||'—')));row.append(text('td',(item.step_id||'업무')+' / '+item.event_type+(item.attempt?' · 시도 '+item.attempt:'')),state,text('td',item.source_service),duration,link);return row;});
 if(cursor===0)el('timeline').replaceChildren();el('timeline').append(...rows);cursor=page.nextCursor;el('more').disabled=!page.hasMore;
 el('completeness').textContent='수집된 이력 기준입니다. 전송 지연·누락 가능성이 있어 전체 완료 여부는 업무 상태와 함께 확인하세요.';
 el('projection').replaceChildren(...page.projections.map(normalize).map(item=>text('p','업무 상태 '+item.status+' · 버전 '+item.aggregate_version)),...(page.steps||[]).map(item=>text('p',item.step_id+' · 시도 '+item.attempt+' · '+item.status)));
 if(page.completenessDetails)el('completeness').append(text('span',' · 완전성 '+page.completeness+' / '+page.completenessDetails.reason+(page.completenessDetails.stepsTruncated?' · 단계 목록 잘림':'')));
}
el('refresh').addEventListener('click',()=>safely(refresh));el('search').addEventListener('submit',event=>{event.preventDefault();cursor=0;safely(flow);});el('more').addEventListener('click',()=>safely(flow));
