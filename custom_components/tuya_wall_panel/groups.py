"""Panel-owned LAN directory. HA mirrors it and never carries group audio."""
import asyncio
from datetime import timedelta
import re
import aiohttp
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.event import async_track_time_interval

def group_url(host, port):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]{0,252}", host) or ".." in host or not 1 <= int(port) <= 65535:
        raise ValueError("Enter a hostname or IPv4 address and API port")
    return f"http://{host}:{int(port)}/api/v1/"

async def directory(hass, host, port, token):
    headers={"Authorization":f"Bearer {token}"} if token else {}
    async with async_get_clientsession(hass).get(group_url(host,port)+"groups",headers=headers,timeout=aiohttp.ClientTimeout(total=5)) as response:
        response.raise_for_status()
        value=await response.json()
    if not isinstance(value,dict) or value.get("v") != 1 or not isinstance(value.get("panels"),list) or len(value["panels"])>65:
        raise ValueError("Unsupported panel directory")
    seen=set()
    for room in value["panels"]:
        if not isinstance(room,dict) or not re.fullmatch(r"panel-[A-Za-z0-9-]{8,80}",str(room.get("id",""))) or room["id"] in seen:
            raise ValueError("Invalid or duplicate room identity")
        seen.add(room["id"])
        if not isinstance(room.get("name","Wall Panel"),str):raise ValueError("Invalid room name")
        if room.get("state","idle") not in ("idle","playing","paused","buffering"):raise ValueError("Invalid room playback state")
    if not isinstance(value.get("presets",[]),list) or len(value.get("presets",[]))>64:raise ValueError("Invalid saved groups")
    for preset in value.get("presets",[]):
        if not isinstance(preset,dict) or not isinstance(preset.get("id"),str) or not isinstance(preset.get("members"),list) or not isinstance(preset.get("leader"),str):raise ValueError("Invalid saved group")
    if not any(p.get("id")==value.get("self") for p in value["panels"]):
        raise ValueError("Panel identity is missing")
    return value

class PanelGroups:
    def __init__(self,controller):
        self.controller=controller;self.data={};self.online=False;self.error=None;self._unsub=None
        self._watchers={};self._lock=asyncio.Lock();self._command_lock=asyncio.Lock();self.players={}
    @property
    def enabled(self):return bool(self.controller.options.get("group_enabled") and self.controller.options.get("group_host"))
    @property
    def panels(self):return {p["id"]:p for p in self.data.get("panels",[])}
    @property
    def presets(self):return {p["id"]:p for p in self.data.get("presets",[]) if not p.get("deleted")}
    def members(self,leader):
        return [p["id"] for p in self.panels.values() if p.get("active") and p.get("leader")==leader]
    def watch(self,key,add,build):
        self._watchers[key]=(add,build,set());self._discover()
    def _discover(self):
        for add,build,seen in self._watchers.values():
            entities=[]
            for kind,items in (("room",self.panels),("preset",self.presets)):
                for key in items:
                    identity=kind+":"+key
                    if identity not in seen:
                        entity=build(kind,key)
                        if entity is not None:entities.append(entity)
                        seen.add(identity)
            if entities:add(entities)
    async def start(self):
        if self.enabled:
            await self.refresh()
            self._unsub=async_track_time_interval(self.controller.hass,lambda _:self.controller.spawn(self.refresh()),timedelta(seconds=5))
    async def stop(self):
        if self._unsub:self._unsub();self._unsub=None
        self.online=False;self._watchers.clear()
    async def refresh(self):
        if not self.enabled or self._lock.locked():return
        async with self._lock:
            options=self.controller.options
            try:
                previous=self.panels
                self.data=await directory(self.controller.hass,options["group_host"],options.get("group_port",8099),options.get("group_token",""))
                renamed=[p for key,p in self.panels.items() if key in previous and previous[key].get("name")!=p.get("name")]
                if renamed:
                    from homeassistant.helpers import device_registry as dr
                    from .const import DOMAIN
                    registry=dr.async_get(self.controller.hass)
                    for room in renamed:
                        device=registry.async_get_device(identifiers={(DOMAIN,"audio_room:"+room["id"])})
                        if device:registry.async_update_device(device.id,name=room["name"])
                self.online=True;self.error=None;self._discover()
            except (aiohttp.ClientError,asyncio.TimeoutError,ValueError,KeyError):
                self.online=False;self.error="Panel group API is unavailable or authentication was rejected"
            self.controller.notify()
    async def command(self,op,**kwargs):
        if not self.enabled:raise HomeAssistantError("Enable Audio groups in the integration configuration first")
        options=self.controller.options;headers={"Authorization":"Bearer "+options["group_token"]} if options.get("group_token") else {}
        async with self._command_lock:
            try:
                async with async_get_clientsession(self.controller.hass).post(group_url(options["group_host"],options.get("group_port",8099))+"group-command",json={"op":op,**kwargs},headers=headers,timeout=aiohttp.ClientTimeout(total=65)) as response:
                    result=await response.json();response.raise_for_status()
                if not result.get("accepted"):
                    failed=[self.panels.get(key,{}).get("name",key) for key in result.get("errors",{})]
                    raise HomeAssistantError("Some rooms did not accept the command: "+", ".join(failed))
            except (aiohttp.ClientError,asyncio.TimeoutError,ValueError) as error:
                raise HomeAssistantError("The panel could not complete the group command; check its group page and room API credentials") from error
            finally:await self.refresh()
    async def library(self,node,folder="root",offset=0):
        if not self.enabled:raise HomeAssistantError("Enable Audio groups connection first")
        options=self.controller.options;headers={"Authorization":"Bearer "+options["group_token"]} if options.get("group_token") else {}
        try:
            async with async_get_clientsession(self.controller.hass).get(group_url(options["group_host"],options.get("group_port",8099))+"library",params={"target":node,"folder":folder,"offset":offset,"limit":100},headers=headers,timeout=aiohttp.ClientTimeout(total=10)) as response:
                value=await response.json();response.raise_for_status()
            if value.get("node")!=node or not isinstance(value.get("tracks"),list) or not isinstance(value.get("folders"),list):raise ValueError("Invalid library")
            if value.get("permissionRequired"):raise HomeAssistantError("Allow music access on the source panel in its group settings")
            return value
        except (aiohttp.ClientError,asyncio.TimeoutError,ValueError) as error:raise HomeAssistantError("Cannot browse this panel's local music library") from error
    def room_ids(self,entity_ids):
        lookup={entity:id for id,entity in self.players.items()}
        if any(entity not in lookup for entity in entity_ids):raise HomeAssistantError("Choose Room audio players belonging to this panel directory")
        return [lookup[entity] for entity in entity_ids]
