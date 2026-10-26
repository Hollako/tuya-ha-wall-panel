// Isolated script tests only. No browser, panel, HA requests or UI operations.
const vm=require('node:vm'),fs=require('node:fs'),assert=require('node:assert/strict');
const source=fs.readFileSync(process.argv[2],'utf8');let checks=0;
function fixture(ha=true){
 let palette={'--card-background-color':'#142326','--primary-text-color':'#edf4f1','--ha-color-fill-neutral-quiet-resting':'#f3f3f3'};
 let props=new Map(),writes=0,interval,pending,observers=0;
 let style={getPropertyValue:k=>(props.get(k)||{}).value||'',getPropertyPriority:k=>(props.get(k)||{}).priority||'',setProperty(k,value,priority=''){writes++;props.set(k,{value,priority});},removeProperty(k){writes++;props.delete(k);}};
 let control={tagName:'HA-SELECT',style,parentElement:{},shadowRoot:null};let other={tagName:'BUTTON',style:{setProperty(){throw Error('unrelated button modified');}}};
 let document={querySelector:()=>ha?{}:null,querySelectorAll:()=>[control,other]};
 let env={window:{},document,CSS:{supports:()=>true},getComputedStyle:()=>({getPropertyValue:k=>palette[k]||''}),MutationObserver:class{constructor(){observers++;}observe(){}},setTimeout:f=>{pending=f;return 1;},setInterval:f=>{interval=f;return 2;},WeakMap,WeakSet};vm.createContext(env);vm.runInContext(source,env);
 return {env,style,palette,props,get writes(){return writes;},get observers(){return observers;},tick(){interval();let f=pending;pending=null;f();}};
}
let a=fixture();assert.match(a.style.getPropertyValue('--ha-color-form-background'),/mdc-select-fill-color/);checks++;
assert.match(a.style.getPropertyValue('--ha-color-form-background-disabled'),/card-background-color/);checks++;
assert.match(a.style.getPropertyValue('--ha-color-fill-warning-quiet-resting'),/warning-color/);checks++;
for(const level of ['quiet','normal']) for(const state of ['resting','hover','active']) {assert.match(a.style.getPropertyValue('--ha-color-fill-primary-'+level+'-'+state),/color-mix.*primary-color.*card-background-color/);checks++;}
assert.notEqual(a.style.getPropertyValue('--ha-color-fill-primary-quiet-hover'),a.style.getPropertyValue('--ha-color-fill-primary-quiet-resting'));checks++;
let writes=a.writes;a.tick();assert.equal(a.writes,writes);checks++;
vm.runInContext(source,a.env);assert.equal(a.observers,1);checks++;
a.palette['--ha-color-fill-neutral-quiet-resting']='#202020';a.tick();assert.equal(a.props.size,0);checks++;
a.palette['--ha-color-fill-neutral-quiet-resting']='#f3f3f3';a.tick();assert.ok(a.props.size>0);checks++;
a.palette['--primary-text-color']='#101010';a.palette['--card-background-color']='#ffffff';a.tick();assert.equal(a.props.size,0);checks++;
a=fixture();a.style.setProperty('--ha-color-form-background','user-custom');a.palette['--card-background-color']='#ffffff';a.tick();assert.equal(a.style.getPropertyValue('--ha-color-form-background'),'user-custom');checks++;
a=fixture(false);assert.equal(a.props.size,0);assert.equal(a.observers,0);checks++;
console.log('PASS: '+checks+' dropdown compatibility checks: legacy dark theme, disabled/warning states, stable idempotency, complete theme/light theme restoration, preserved external edit, non-HA no-op. No panel checks.');
