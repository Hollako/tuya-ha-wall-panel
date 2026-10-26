"""Optional app-management channel; separate from the legacy hardware availability."""
from homeassistant.components import mqtt
from homeassistant.core import callback
from homeassistant.exceptions import HomeAssistantError
import json
from .dashboard import validate_dashboard_url

class PanelApp:
    def __init__(self,controller):self.controller=controller;self._online=False;self.dashboard=None;self.dashboard_error=None;self._unsubs=[]
    @property
    def online(self):return self._online and self.controller.online
    @online.setter
    def online(self,value):self._online=bool(value)
    async def start(self):
        self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/app/availability",self._state,1))
        self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/app/dashboard/state",self._dashboard_state,1))
        if self.manages_dashboard():await self.send_dashboard()
    async def stop(self):
        for unsub in self._unsubs:unsub()
        self._unsubs.clear();self.online=False
    @callback
    def _state(self,message):
        if message.payload not in ("online","offline"):return
        was=self._online;self.online=message.payload=="online";self.controller.notify()
        if was and not self._online:
            self.controller.hardware.display_online=False;self.controller.hardware.knob_input_online=False
            if self.controller.audio.owner=="app":self.controller.audio.online=False;self.controller.hardware.amplifier_online=False
            if self.controller.device.display_data.get("owner")=="app":self.controller.device.display_data={**self.controller.device.display_data,"available":False}
            self.controller._sent=None;self.controller.notify()
        if self.online and not was and self.manages_dashboard():self.controller.spawn(self.send_dashboard())
    def manages_dashboard(self):return self.controller.options.get("dashboard_managed",False) or bool(self.controller.options.get("dashboard_url",""))
    @callback
    def _dashboard_state(self,message):
        try:
            data=json.loads(message.payload)
            if not isinstance(data,dict) or data.get("v")!=1:return
            if "error" in data:self.dashboard_error="Panel rejected dashboard update";self.dashboard=None
            else:self.dashboard=validate_dashboard_url(data["url"]);self.dashboard_error=None
        except (ValueError,TypeError,KeyError):return
        self.controller.notify()
    async def send_dashboard(self):
        value=validate_dashboard_url(self.controller.options.get("dashboard_url",""))
        await mqtt.async_publish(self.controller.hass,self.controller.base+"/app/dashboard/set",json.dumps({"v":1,"url":value}),qos=1,retain=True)
    async def open_setup(self):
        await self.command("OPEN_SETUP")
    async def command(self,command):
        if command not in {"OPEN_SETUP","RELOAD_DASHBOARD","RESTART_APP"}:raise HomeAssistantError("Unknown app command")
        if not self.online:raise HomeAssistantError("Wall Panel app runtime is offline")
        await mqtt.async_publish(self.controller.hass,self.controller.base+"/app/command/set",command,qos=1,retain=False)
