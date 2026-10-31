const vm=require('vm');
class Node {
 constructor(tag='div'){this.tag=tag;this.children=[];this.textContent='';this.checked=false;this.hidden=false;this.disabled=false;this.dataset={};this.listeners={};this.open=false;}
 appendChild(node){if(node.parentNode)node.remove();this.children.push(node);node.parentNode=this;return node;}
 prepend(node){if(node.parentNode)node.remove();this.children.unshift(node);node.parentNode=this;}
 get options(){return this.children.filter(n=>n.tag==='option');}
 get value(){if(this.tag==='select')return this.options.some(n=>n.value===this._value)?this._value:this.options[0]?.value||'';return String(this._value??'');}
 set value(value){this._value=String(value);}
 get lastChild(){return this.children.at(-1);}
 querySelector(tag){for(const child of this.children){if(child.tag===tag)return child;const found=child.querySelector(tag);if(found)return found;}return null;}
 remove(){if(this.parentNode){this.parentNode.children=this.parentNode.children.filter(n=>n!==this);this.parentNode=null;}}
 replaceWith(node){const parent=this.parentNode,index=parent.children.indexOf(this);if(node.parentNode)node.remove();parent.children[index]=node;node.parentNode=parent;this.parentNode=null;}
 replaceChildren(){for(const child of this.children)child.parentNode=null;this.children=[];}
 addEventListener(type,listener){this.listeners[type]=listener;}
 removeAttribute(key){delete this[key];}
 setAttribute(key,value){this[key]=value;}
}
function run(html,fetch){
 const nodes={},actions=[];for(const m of html.matchAll(/<(\w+)[^>]*\bid="([^"]+)"[^>]*>/g)){const node=nodes[m[2]]=new Node(m[1]);const value=m[0].match(/\bvalue="([^"]*)"/);if(value)node.value=value[1];}
 for(const m of html.matchAll(/<select\b[^>]*\bid="([^"]+)"[^>]*>([\s\S]*?)<\/select>/g))for(const choice of m[2].matchAll(/<option\b[^>]*value="([^"]*)"[^>]*>([^<]*)/g)){const option=new Node('option');option.value=choice[1];option.textContent=choice[2];nodes[m[1]].appendChild(option);}
 for(const m of html.matchAll(/<button\b[^>]*data-action="([^"]+)"[^>]*>([^<]*)/g)){const node=new Node('button');node.dataset.action=m[1];node.textContent=m[2];actions.push(node);}
 const sandbox={document:{activeElement:null,getElementById:id=>nodes[id],createElement:tag=>new Node(tag),createTextNode:text=>Object.assign(new Node('text'),{textContent:text}),querySelectorAll:()=>actions},window:{addEventListener(){}},location:{protocol:'http:',hostname:'panel.test',port:'8099'},confirm:()=>true,AbortController,console,setTimeout:()=>1,clearTimeout(){},setInterval(){},fetch,URL:{createObjectURL:()=>"blob:test",revokeObjectURL(){}}};
 vm.createContext(sandbox);vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],sandbox);return {nodes,actions,sandbox,evaluate:code=>vm.runInContext(code,sandbox)};
}
module.exports={Node,run,flush:()=>new Promise(resolve=>setImmediate(resolve))};
