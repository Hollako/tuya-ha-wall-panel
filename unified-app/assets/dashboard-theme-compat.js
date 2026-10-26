(function () {
  'use strict';
  if (window.__wallPanelThemeCompat || !document.querySelector('home-assistant')) return;
  var state = window.__wallPanelThemeCompat = {version: 2, fixed: 0};
  var roots = new WeakSet(), edits = new WeakMap(), timer;
  var base = 'var(--mdc-select-fill-color,var(--card-background-color))';
  function primaryTint(percent) {
    return CSS.supports('color','color-mix(in srgb,red 12%,black)')
      ? 'color-mix(in srgb,var(--primary-color,var(--accent-color,#03a9f4)) '+percent+'%,'+base+')' : base;
  }
  var values = {
    '--ha-color-form-background': base,
    '--ha-color-form-background-disabled': base,
    '--ha-color-fill-neutral-quiet-resting': base,
    '--ha-color-fill-neutral-quiet-hover': 'var(--secondary-background-color,var(--card-background-color))',
    '--ha-color-fill-neutral-quiet-active': base,
    '--ha-color-fill-neutral-normal-resting': base,
    '--ha-color-fill-neutral-normal-hover': 'var(--secondary-background-color,var(--card-background-color))',
    '--ha-color-fill-neutral-normal-active': base,
    '--ha-color-fill-primary-quiet-resting': primaryTint(12),
    '--ha-color-fill-primary-quiet-hover': primaryTint(18),
    '--ha-color-fill-primary-quiet-active': primaryTint(24),
    '--ha-color-fill-primary-normal-resting': primaryTint(20),
    '--ha-color-fill-primary-normal-hover': primaryTint(28),
    '--ha-color-fill-primary-normal-active': primaryTint(35),
    '--ha-color-on-primary-quiet': 'var(--primary-color,var(--primary-text-color))',
    '--ha-color-on-primary-normal': 'var(--primary-text-color)',
    '--ha-color-on-neutral-quiet': 'var(--secondary-text-color)',
    '--ha-color-on-neutral-normal': 'var(--primary-text-color)',
    '--ha-color-border-neutral-quiet': 'var(--divider-color)',
    '--ha-color-border-neutral-normal': 'var(--divider-color)',
    '--ha-color-fill-warning-quiet-resting': CSS.supports('color', 'color-mix(in srgb,red 12%,black)')
      ? 'color-mix(in srgb,var(--warning-color,#ffa600) 12%,' + base + ')' : base
  };
  function brightness(value) {
    value = value.trim();
    var rgb;
    if (/^#[0-9a-f]{3}$/i.test(value)) value = '#' + value.slice(1).split('').map(function (c) {return c+c;}).join('');
    if (/^#[0-9a-f]{6}$/i.test(value)) rgb = [1,3,5].map(function (i) {return parseInt(value.slice(i,i+2),16);});
    else {var match = /^rgba?\(([^)]+)\)$/i.exec(value);if (!match) return null;rgb=match[1].split(/[, /]+/).slice(0,3).map(Number);}
    if (rgb.length!==3 || rgb.some(function (n) {return !isFinite(n);})) return null;
    return (rgb[0]*0.2126+rgb[1]*0.7152+rgb[2]*0.0722)/255;
  }
  function apply(element) {
    // Read the surrounding theme, never our own overridden palette. This also
    // lets a later switch to a complete dark theme or light theme restore it.
    var parent=element.parentElement || element.getRootNode().host;
    if (!parent) return;
    var theme=getComputedStyle(parent);
    var card=brightness(theme.getPropertyValue('--card-background-color'));
    var text=brightness(theme.getPropertyValue('--primary-text-color'));
    var palette=brightness(theme.getPropertyValue('--ha-color-fill-neutral-quiet-resting'));
    var needed=card!==null && text!==null && palette!==null && card<0.3 && text>0.65 && palette>0.75;
    var original=edits.get(element);
    if (!needed) {
      if (original) {
        Object.keys(values).forEach(function (key) {
          if(element.style.getPropertyValue(key)!==values[key]) return;
          var old=original[key];
          if(old.value) element.style.setProperty(key,old.value,old.priority);else element.style.removeProperty(key);
        });
        edits.delete(element);
      }
      return;
    }
    if (!original) {
      original={};Object.keys(values).forEach(function (key) {original[key]={value:element.style.getPropertyValue(key),priority:element.style.getPropertyPriority(key)};});
      edits.set(element,original);
    }
    Object.keys(values).forEach(function (key) {if(element.style.getPropertyValue(key)!==values[key]) element.style.setProperty(key,values[key]);});
    state.fixed++;
  }
  var observer=new MutationObserver(function () {schedule();});
  function scan(root) {
    if (!roots.has(root)) {roots.add(root);observer.observe(root,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style']});}
    root.querySelectorAll('*').forEach(function (element) {
      // Scoped to HA's new picker/select controls. Other dashboards, cards,
      // native media controls and intentional white buttons are untouched.
      if(element.tagName==='HA-SELECT' || element.tagName==='HA-COMBO-BOX') apply(element);
      if(element.shadowRoot) scan(element.shadowRoot);
    });
  }
  function refresh() {timer=null;state.fixed=0;scan(document);}
  function schedule() {if(!timer) timer=setTimeout(refresh,150);}
  refresh();
  // Covers custom elements whose shadow roots appear after their first insertion.
  setInterval(schedule,5000);
}());
