// Run the shipped group page with fake DOM/HTTP; never starts panel audio.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync('airplay-app/android/app/src/main/assets/groups.html','utf8');
class Node {
 constructor(tag='div'){this.tag=tag;this.children=[];this.textContent='';this.checked=false;this.hidden=false;this.disabled=false;}
 appendChild(node){this.children.push(node);node.parentNode=this;return node;}
 get options(){return this.children.filter(n=>n.tag==='option');}
 get value(){if(this.tag==='select')return this.options.some(n=>n.value===this._value)?this._value:this.options[0]?.value||'';return String(this._value??'');}
 set value(value){this._value=String(value);}
 get lastChild(){return this.children.at(-1);}
 querySelector(tag){return this.children.find(n=>n.tag===tag);}
 remove(){this.parentNode.children=this.parentNode.children.filter(n=>n!==this);}
 replaceChildren(){this.children=[];}
}
const A='panel-aaaaaaaa',B='panel-bbbbbbbb';const nodes={};for(const m of html.matchAll(/<(\w+)[^>]*\bid="([^"]+)"/g))nodes[m[2]]=new Node(m[1]);
const state={v:1,self:A,panels:[{id:A,name:'Living',online:true,authorized:true,active:true,leader:A,state:'playing',volume:.1,muted:false,roomDelayMs:20},{id:B,name:'Kitchen',online:true,authorized:true,active:true,leader:A,state:'playing',volume:.2,muted:false,roomDelayMs:0}],presets:[]};
const requests=[];let unauthorized=false;
const sandbox={document:{activeElement:null,getElementById:id=>nodes[id],createElement:tag=>new Node(tag),createTextNode:text=>Object.assign(new Node('text'),{textContent:text})},window:{},AbortController,console,setTimeout:()=>1,clearTimeout(){},setInterval(){},fetch:async(url,options)=>{
 requests.push({url,options});const body=options.body?JSON.parse(options.body):null;
 if(body?.op==='room'){const room=state.panels.find(p=>p.id===body.target);for(const key of ['volume','muted','roomDelayMs','name'])if(key in body)room[key]=body[key];}
 const library={node:A,folder:'all',folders:[],tracks:[{id:'audio-42',title:'Local song',artist:'Artist'}],total:1,nextOffset:null};return {status:unauthorized?401:200,ok:!unauthorized,json:async()=>body?{accepted:true}:url.includes('/library?')?library:JSON.parse(JSON.stringify(state))};
}};
vm.createContext(sandbox);vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],sandbox);const flush=()=>new Promise(resolve=>setImmediate(resolve));const evaluate=code=>vm.runInContext(code,sandbox);
(async()=>{
 await flush();assert.equal(nodes.rooms.children.length,2);assert.equal(nodes.leader.value,A);assert.equal(evaluate('selected().length'),2);
 await nodes.apply.onclick();let body=JSON.parse(requests.find(r=>r.options.body).options.body);assert.deepEqual(body,{op:'set_group',leader:A,members:[A,B]});
 assert(nodes.bluetoothGroup.disabled);state.panels[0].bluetoothGroupAvailable=true;await evaluate('refresh()');assert(!nodes.bluetoothGroup.disabled);
 await nodes.bluetoothGroup.onclick();body=JSON.parse(requests.filter(r=>r.options.body).at(-1).options.body);assert.deepEqual(body,{op:'group_input',leader:A,input:'bluetooth',members:[A,B]});
 const room=evaluate(`cards.get('${B}')`);room.volume.value=2;await room.volume.onchange();assert.equal(state.panels[1].volume,.02);assert.equal(state.panels[0].volume,.1);
 await room.mute.onclick();assert.equal(state.panels[1].muted,true);assert.equal(state.panels[1].volume,.02);
 await nodes.musicAll.onclick();assert(nodes.musicStatus.textContent.includes('1 tracks'));const song=nodes.musicList.children[0];await song.children.find(n=>n.tag==='button'&&n.textContent==='Play selected rooms').onclick();let selectedTrack=JSON.parse(requests.filter(r=>r.options.body).at(-1).options.body);assert.deepEqual(selectedTrack,{op:'play_track',leader:A,track:'audio-42',members:[A,B]});
 nodes.groupName.value='Ground Floor';await nodes.save.onclick();body=JSON.parse(requests.filter(r=>r.options.body).at(-1).options.body);assert.equal(body.op,'save');assert.equal(body.name,'Ground Floor');assert.deepEqual(body.members,[A,B]);
 evaluate(`checks.get('${B}').checked=true;checks.get('${B}').onchange()`);state.panels[1].active=false;await evaluate('refresh()');assert(evaluate(`checks.get('${B}').checked`),'Polling replaced an unfinished room selection');
 sandbox.document.activeElement=room.name;room.name.value='Edited room';await evaluate('refresh()');assert.equal(room.name.value,'Edited room');
 unauthorized=true;await evaluate('refresh()');assert.equal(nodes.auth.hidden,false);assert(nodes.message.textContent.includes('token'));
 assert(requests.every(r=>r.url==='/api/v1/groups'||r.url==='/api/v1/group-command'||r.url.startsWith('/api/v1/library?')));
 console.log('PASS: room discovery rendering, exact membership, independent volume/mute, presets, pending edits, authentication and panel-only API routes');
})().catch(error=>{console.error(error);process.exitCode=1;});
