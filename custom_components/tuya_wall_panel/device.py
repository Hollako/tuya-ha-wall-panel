"""Panel management over the existing authenticated MQTT connection."""
import json
import time
import math
from datetime import datetime,timezone
from homeassistant.components import mqtt
from homeassistant.core import callback
from homeassistant.exceptions import HomeAssistantError

class PanelDevice:
    def __init__(self,controller):
        self.controller=controller;self.data={};self.image=None;self.image_updated=None
        self._unsubs=[];self._requested=0;self._requesting=False
        self.display_data={}
        self.management_owner="legacy";self.management_online=True

    @property
    def available(self):return self.controller.online and bool(self.data) and self.management_online and (self.management_owner!="app" or self.controller.app.online)
    @property
    def display_available(self):return self.controller.online and bool(self.data) and (not self.display_data or self.display_data.get("available",False))

    async def start(self):
        self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/device/state",self._state,1))
        self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/device/screenshot",self._image,1,encoding=None))
        self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/display/state",self._display_state,1))

    async def stop(self):
        for unsub in self._unsubs:unsub()
        self._unsubs.clear();self.image=None

    @callback
    def _state(self,message):
        try:
            data=json.loads(message.payload)
            if not isinstance(data,dict) or data.get("protocol_version")!=1:return
            if not isinstance(data.get("display_on"),bool):return
            level=data.get("brightness")
            if isinstance(level,bool) or not isinstance(level,int) or not 1<=level<=255:return
            owner=data.get("owner","legacy");online=data.get("management_available",True)
            if owner not in ("app","legacy") or not isinstance(online,bool):return
            self.management_owner=owner;self.management_online=online
            self.data={**data,**self.display_data};self.controller.notify()
        except (ValueError,TypeError):return

    @callback
    def _display_state(self,message):
        try:
            data=json.loads(message.payload)
            if not isinstance(data,dict) or data.get("protocol_version")!=1 or data.get("owner") not in ("app","legacy"):return
            if not isinstance(data.get("available"),bool) or not isinstance(data.get("display_on"),bool):return
            for key,minimum,maximum in (("brightness",1,255),("actual_brightness",0,255),("dim_after",0,86400),("off_after",0,86400),("dim_brightness",1,255)):
                value=data.get(key)
                if isinstance(value,bool) or not isinstance(value,int) or not minimum<=value<=maximum:return
            if data.get("phase") not in ("awake","dim","off"):return
            if data["owner"]=="app" and not self.controller.app.online:data={**data,"available":False}
            self.display_data=data;self.data={**self.data,**data};self.controller.notify()
        except (ValueError,TypeError):return

    async def policy(self,key,value):
        if key not in ("dim_after","off_after","dim_brightness"):raise HomeAssistantError("Unknown display setting")
        if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or int(value)!=value:raise HomeAssistantError("Use a whole number")
        limit=100 if key=="dim_brightness" else 1440
        if not (1 if key=="dim_brightness" else 0)<=value<=limit:raise HomeAssistantError("Display setting out of range")
        if not self.display_available or not self.display_data:raise HomeAssistantError("Display controller is offline")
        raw=max(1,min(255,round(value*255/100))) if key=="dim_brightness" else int(value*60)
        updated={**self.display_data,key:raw}
        if updated["dim_after"] and updated["off_after"] and updated["dim_after"]>=updated["off_after"]:
            raise HomeAssistantError("Dim time must be earlier than display-off time; 0 disables a timer")
        await mqtt.async_publish(self.controller.hass,self.controller.base+f"/display/policy/{key}/set",str(raw),qos=1,retain=False)

    @callback
    def _image(self,message):
        data=message.payload
        if message.retain or not isinstance(data,bytes) or not 32<=len(data)<=8*1024*1024:return
        if not data.startswith(b"\x89PNG\r\n\x1a\n") or not data.endswith(b"\x00\x00\x00\x00IEND\xaeB`\x82"):return
        self.image=data;self.image_updated=datetime.now(timezone.utc);self.controller.notify()

    async def command(self,command):
        if command not in ("DISPLAY_ON","DISPLAY_OFF","REBOOT","SCREENSHOT"):raise HomeAssistantError("Unknown panel command")
        if not (self.display_available if command.startswith("DISPLAY_") else self.available):raise HomeAssistantError("Tuya Wall Panel controller is offline")
        await mqtt.async_publish(self.controller.hass,self.controller.base+"/device/command/set",command,qos=1,retain=False)

    async def brightness(self,value):
        if isinstance(value,bool) or not isinstance(value,int) or not 1<=value<=255:raise HomeAssistantError("Brightness must be between 1 and 255")
        if not self.display_available:raise HomeAssistantError("Tuya Wall Panel display is offline")
        await mqtt.async_publish(self.controller.hass,self.controller.base+"/device/brightness/set",str(value),qos=1,retain=False)

    async def refresh(self):
        if not self.available or self._requesting or time.monotonic()-self._requested<5:return
        self._requesting=True
        try:
            await self.command("SCREENSHOT");self._requested=time.monotonic()
        finally:self._requesting=False

    @callback
    def tick(self):
        if self.management_owner!="app" and self.available and not self._requesting and time.monotonic()-self._requested>=300:
            self.controller.spawn(self.refresh())
