// Exercise the actual page JavaScript with isolated fake HTTP/DOM, not panel playback.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync('airplay-app/android/app/src/main/assets/timing.html','utf8');
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(x=>x[1]);assert.equal(new Set(ids).size,ids.length);
const elements=Object.fromEntries(ids.map(id=>[id,{value:'',textContent:'',hidden:false,disabled:false,events:{},addEventListener(name,fn){this.events[name]=fn;},removeAttribute(name){delete this[name];}}]));
const requests=[];let group=37,air=-21,level=.05,unauthorized=false,failGroup=false;
const status=()=>({receiver:{name:'Test room',state:'IDLE',remote:{}},stream:{},audio:{latencyOffsetMs:air},group:{active:true,role:'receiver',status:'Connected',roomDelayMs:group,volume:level,muted:false}});
const sandbox={document:{getElementById:id=>elements[id],hidden:false},window:{addEventListener(){}},AbortController,URL,console,setTimeout:()=>1,clearTimeout(){},setInterval(){},fetch:async(url,options)=>{
 requests.push({url,options});let code=unauthorized?401:200;
 if(options.method==='POST'){
  const body=JSON.parse(options.body);
  if(url.endsWith('/group-timing')){if(failGroup)code=500;else group=body.roomDelayMs;}
  else if(url.endsWith('/group-volume'))level=body.volume;
  else if(url.endsWith('/timing'))air=body.latencyOffsetMs;
 }
 return {status:code,ok:code===200,json:async()=>options.method==='POST'?{accepted:true}:status()};
}};
vm.createContext(sandbox);vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],sandbox);
const flush=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
 await flush();assert.equal(Number(elements.groupDelay.value),37);assert.equal(Number(elements.delay.value),-21);
 assert.equal(Number(elements.groupVolume.value),5);elements.volumeDown.onclick();await flush();assert.equal(level,.04);assert.equal(air,-21);assert.equal(group,37);
 elements.groupMinus.onclick();await vm.runInContext('groupSend()',sandbox);
 assert.equal(group,32);assert.equal(air,-21);assert(requests.at(-1).url.endsWith('/group-timing'));
 elements.plus.onclick();await vm.runInContext('send()',sandbox);assert.equal(air,-16);assert.equal(group,32);
 vm.runInContext('groupChange(-50)',sandbox);await vm.runInContext('refresh()',sandbox);assert.equal(Number(elements.groupDelay.value),-50,'Polling overwrote a pending user edit');
 await vm.runInContext('groupSend()',sandbox);assert.equal(group,-50);
 failGroup=true;vm.runInContext('groupChange(45)',sandbox);await vm.runInContext('groupSend()',sandbox);assert(elements.groupMessage.textContent.includes('Not confirmed saved'));
 await vm.runInContext('refresh()',sandbox);assert.equal(Number(elements.groupDelay.value),45,'Failed write was silently hidden by polling');
 unauthorized=true;await vm.runInContext('refresh()',sandbox);assert(elements.groupDelay.disabled&&elements.delay.disabled);assert.equal(elements.auth.hidden,false);
 console.log('PASS: independent group/AirPlay writes, pending-edit protection, failed-save feedback and authentication gating');
})().catch(error=>{console.error(error);process.exitCode=1;});
