const assert=require('node:assert/strict');
const fs=require('node:fs');const vm=require('node:vm');
class Element {
 constructor(tag='div'){this.tag=tag;this.children=[];this.value='';this.textContent='';this.listeners={};this.style={};this.classList={add(){}};}
 set innerHTML(value){throw Error('Untrusted HTML rendering is forbidden');}
 append(...nodes){this.children.push(...nodes);}
 replaceChildren(...nodes){this.children=[...nodes];this.textContent='';}
 addEventListener(event,fn){this.listeners[event]=fn;}
}
const elements=new Map();const calls=[];const replies=[];
const document={getElementById(id){if(!elements.has(id))elements.set(id,new Element());return elements.get(id);},createElement(tag){return new Element(tag);}};
const context=vm.createContext({document,console,fetch:async(url,options)=>{calls.push({url,options});assert.ok(replies.length,'Expected API fixture');const r=replies.shift();return {status:r.status||200,ok:(r.status||200)<400,json:async()=>r.body};}});
vm.runInContext(fs.readFileSync('test-service/src/main/resources/static/js/operations.js','utf8'),context);
['operator','password','services','instances','delivery','process','timeline','more','completeness','notice','session','impact','projection','observed','incidents','errors'].forEach(id=>document.getElementById(id));
const idle=()=>new Promise(resolve=>setImmediate(resolve));
async function settle(){for(let i=0;i<10;i++)await idle();}
(async()=>{
 replies.push({body:{accessToken:'ephemeral-access'}},{body:{observedAt:'2026-10-20',collectorState:'UP',services:{'<script>':'UP'},instances:[]}},{body:[]},{body:[]},{body:{configured:false,pending:1}});
 elements.get('operator').value='operator';elements.get('password').value='fixture-password';
 elements.get('login').listeners.submit({preventDefault(){}});await settle();
 assert.equal(elements.get('password').value,'');assert.equal(calls[1].options.headers.Authorization,'Bearer ephemeral-access');
 assert.equal(elements.get('services').children[0].children[0].textContent,'<script>');
 assert.ok(elements.get('delivery').textContent.includes('미설정'));
 replies.push({body:{events:[{event_type:'STEP_FINISHED',status:'FAILED',trace_id:'a',span_id:'b',parent_span_id:'c',elapsed_ms:25,attempt:1}],projections:[{STATUS:'FAILED',AGGREGATE_VERSION:3}],nextCursor:3,hasMore:false}});
 elements.get('process').value='process-fixture';elements.get('search').listeners.submit({preventDefault(){}});await settle();
 assert.equal(elements.get('timeline').children.length,1);assert.equal(elements.get('more').disabled,true);assert.ok(elements.get('completeness').textContent.includes('누락'));
 elements.get('logout').listeners.click();assert.equal(elements.get('timeline').children.length,0);
 replies.push({status:403,body:{code:'ACCESS_DENIED'}});elements.get('refresh').listeners.click();await settle();
 assert.equal(calls.at(-1).options.headers.Authorization,undefined);assert.ok(elements.get('notice').textContent.includes('권한'));
 assert.equal(replies.length,0);console.log('PASS: operations login, memory token, safe text rendering, flow page, logout and role-denial display');
})().catch(error=>{console.error(error);process.exitCode=1;});
