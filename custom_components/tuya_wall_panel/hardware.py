"""Native HA ownership of the existing panel protocol; no driver changes."""
from dataclasses import dataclass
import json
import math
import re
from homeassistant.components import mqtt
from homeassistant.core import callback
from homeassistant.exceptions import HomeAssistantError
from .entity import KnobEntity
from .const import DOMAIN

TEXT_PATTERN = r"^[ -~\u00a0-\u00ff\u2013\u2014\u2019\u2022\u2212]*$"

@dataclass(frozen=True)
class HardwareSpec:
    domain: str
    key: str
    name: str
    path: str
    availability: str = "display"
    options: tuple = ()
    maximum: int = 0
    icon: str = "mdi:monitor-small"

SPECS = (
    *(HardwareSpec("switch", f"relay_{i}", f"Relay {i}", f"/relay/{i}", "module", icon="mdi:electric-switch") for i in (1,2)),
    *(HardwareSpec("text", f"knob_display_{field}", f"Knob display {field}", f"/knob/display/{field}", maximum=maximum, icon="mdi:format-text") for field,maximum in (("title",32),("value",16),("detail",32))),
    HardwareSpec("switch","knob_display_power","Knob display","/knob/display/power"),
    HardwareSpec("switch","knob_display_follow_main","Knob display follow main screen","/knob/display/follow_main"),
    HardwareSpec("switch","knob_activity_mode","Knob activity mode","/knob/display/activity",icon="mdi:timer-outline"),
    HardwareSpec("select","knob_display_color","Knob display accent color","/knob/display/color",options=("Cyan","White","Red","Green","Blue","Amber","Purple"),icon="mdi:palette"),
    HardwareSpec("select","knob_led_color","Knob LED color","/knob/led/color",options=("Red","White"),icon="mdi:led-on"),
    HardwareSpec("number","audio_hardware_level","Audio hardware output level","/audio/hardware_level","amplifier",maximum=100,icon="mdi:amplifier"),
)
EVENTS = {"knob": ("rotate_left","rotate_right","press","double_press","long_press"),
          **{f"button_{i}": ("press","double_press") for i in range(1,5)}}
LEGACY_ENTITIES = tuple((s.domain,s.key) for s in SPECS if s.key!="knob_activity_mode") + (("switch","audio_external_speakers"),) + tuple(("event",k) for k in EVENTS)

def event_path(key):
    return "/knob/event" if key=="knob" else f"/button/{key[-1]}/event"

class PanelHardware:
    def __init__(self,controller):
        self.controller=controller;self.values={};self.module_online=False;self.display_online=False
        self.buttons_enabled=True;self.relays_enabled=True
        self.knob_input_online=True # Legacy bridge predates the dedicated availability topic.
        self.amplifier_online=True
        self._unsubs=[];self._events={key:set() for key in EVENTS}

    async def start(self):
        for suffix in ("/module/availability","/module/capabilities","/knob/display/availability","/knob/input/availability","/audio/amplifier/availability",*(s.path+"/state" for s in SPECS)):
            self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+suffix,self._state,1))
        for key in EVENTS:
            self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+event_path(key),self._event,1))

    async def stop(self):
        for unsub in self._unsubs:unsub()
        self._unsubs.clear()
        for listeners in self._events.values():listeners.clear()

    def available(self,kind):
        if kind=="amplifier":return self.controller.online and self.amplifier_online
        if kind=="knob_input":return self.controller.online and self.knob_input_online
        return self.controller.online and (kind=="bridge" or (self.module_online if kind=="module" else self.display_online))

    @callback
    def _state(self,message):
        path=message.topic[len(self.controller.base):]
        if path=="/module/availability":self.module_online=message.payload=="online"
        elif path=="/module/capabilities":
            try:data=json.loads(message.payload)
            except (ValueError,TypeError):return
            if not isinstance(data,dict) or not isinstance(data.get("buttons_enabled"),bool) or not isinstance(data.get("relays_enabled"),bool):return
            self.buttons_enabled=data["buttons_enabled"];self.relays_enabled=data["relays_enabled"]
        elif path=="/knob/input/availability":self.knob_input_online=message.payload=="online"
        elif path=="/audio/amplifier/availability":self.amplifier_online=message.payload=="online"
        elif path=="/knob/display/availability":
            was=self.display_online;self.display_online=message.payload=="online"
            if self.display_online and not was:
                self.controller._sent=None;self.controller.spawn(self.controller.render())
        else:
            spec=next((s for s in SPECS if s.path+"/state"==path),None)
            if spec is None:return
            value=message.payload
            if spec.domain=="switch":
                if value not in ("ON","OFF"):return
            elif spec.domain=="select":
                if value not in spec.options:return
            elif spec.domain=="text":
                if not isinstance(value,str) or len(value)>spec.maximum or not re.fullmatch(TEXT_PATTERN,value):return
            else:
                try:value=float(value)
                except (ValueError,TypeError):return
                if not math.isfinite(value) or not 0<=value<=100:return
            self.values[spec.key]=value
        self.controller.notify()

    @callback
    def _event(self,message):
        if message.retain:return
        key=next((k for k in EVENTS if self.controller.base+event_path(k)==message.topic),None)
        if key is None or key!="knob" and not self.buttons_enabled or not self.available("knob_input" if key=="knob" else "module"):return
        try:data=json.loads(message.payload)
        except (ValueError,TypeError):return
        if not isinstance(data,dict) or data.get("event_type") not in EVENTS[key]:return
        for listener in tuple(self._events[key]):listener(data)
        if self.controller.device_id:
            self.controller.hass.bus.async_fire(DOMAIN+"_event",{"device_id":self.controller.device_id,"control":key,"event_type":data["event_type"]})

    @callback
    def listen(self,key,listener):
        self._events[key].add(listener)
        return lambda:self._events[key].discard(listener)

    async def command(self,spec,value):
        if spec.key.startswith("relay_") and not self.relays_enabled:raise HomeAssistantError("Relay module is disabled")
        if not self.available(spec.availability):raise HomeAssistantError("Wall Panel hardware is offline")
        if spec.domain=="switch":valid=value in ("ON","OFF")
        elif spec.domain=="select":valid=value in spec.options
        elif spec.domain=="text":valid=isinstance(value,str) and len(value)<=spec.maximum and bool(re.fullmatch(TEXT_PATTERN,value))
        else:valid=not isinstance(value,bool) and isinstance(value,(int,float)) and math.isfinite(value) and 0<=value<=100 and value==int(value)
        if not valid:raise HomeAssistantError("Invalid Wall Panel control value")
        await mqtt.async_publish(self.controller.hass,self.controller.base+spec.path+("/set"),str(value if spec.domain!="number" else int(value)),qos=1,retain=False)

class HardwareEntity(KnobEntity):
    def __init__(self,controller,spec):
        super().__init__(controller,spec.key)
        self.spec=spec
        self._attr_unique_id=controller.base.rsplit("/",1)[-1]+"_"+spec.key
        self._attr_name=spec.name;self._attr_icon=spec.icon

    @property
    def available(self):
        h=self.controller.hardware
        return h.available(self.spec.availability) and (h.relays_enabled if self.spec.key.startswith("relay_") else h.buttons_enabled if self.spec.key.startswith("button_") else True)

    @property
    def hardware_value(self):return self.controller.hardware.values.get(self.spec.key)

    async def send(self,value):await self.controller.hardware.command(self.spec,value)
