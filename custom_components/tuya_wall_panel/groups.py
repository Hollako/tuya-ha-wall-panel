"""Panel-owned LAN directory. HA mirrors it and never carries group audio."""
import asyncio
from datetime import timedelta
import re
import logging
import json
import ipaddress
import hashlib
import hmac
import secrets
import aiohttp
from homeassistant.components import mqtt
from homeassistant.core import callback
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.event import async_track_time_interval
from homeassistant.helpers.storage import Store
from .const import DOMAIN

_LOGGER=logging.getLogger(__name__)

def endpoint_message(payload):
    if not isinstance(payload,str) or len(payload)>1024:raise ValueError("Invalid endpoint announcement")
    value=json.loads(payload)
    if not isinstance(value,dict) or type(value.get("v")) is not int or value["v"]!=1:raise ValueError("Invalid endpoint version")
    key=value.get("id");host=value.get("host");port=value.get("port")
    if not isinstance(key,str) or not re.fullmatch(r"panel-[A-Za-z0-9-]{8,80}",key):raise ValueError("Invalid panel identity")
    if not isinstance(host,str):raise ValueError("Invalid panel address")
    address=ipaddress.IPv4Address(host)
    if address.is_loopback or address.is_link_local or address.is_multicast or address.is_unspecified or str(address)=="255.255.255.255":raise ValueError("Invalid panel address")
    if type(port) is not int or not 1<=port<=65535:raise ValueError("Invalid API port")
    return key,str(address),port

async def verify_endpoint(hass,host,port,key,token):
    """Verify identity without sending an API secret to an unverified address."""
    nonce=secrets.token_hex(32)
    async with async_get_clientsession(hass).get(group_url(host,port)+"identity",params={"challenge":nonce},allow_redirects=False,timeout=aiohttp.ClientTimeout(total=5)) as response:
        response.raise_for_status();value=await response.json()
    if not isinstance(value,dict) or value.get("v")!=1 or value.get("id")!=key:raise ValueError("Panel identity mismatch")
    if token:
        wanted=hmac.new(token.encode(),f"wallpanel-address-v1\n{key}\n{nonce}".encode("ascii"),hashlib.sha256).hexdigest()
        proof=value.get("proof")
        if not isinstance(proof,str) or not hmac.compare_digest(wanted,proof):raise ValueError("Panel identity proof rejected")
    data=await directory(hass,host,port,token)
    if data["self"]!=key:raise ValueError("Panel directory identity mismatch")
    return data

def group_url(host, port):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]{0,252}", host) or ".." in host or not 1 <= int(port) <= 65535:
        raise ValueError("Enter a hostname or IPv4 address and API port")
    return f"http://{host}:{int(port)}/api/v1/"

async def directory(hass, host, port, token):
    headers={"Authorization":f"Bearer {token}"} if token else {}
    async with async_get_clientsession(hass).get(group_url(host,port)+"groups",headers=headers,allow_redirects=False,timeout=aiohttp.ClientTimeout(total=5)) as response:
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
        self._mqtt_unsub=None;self._pending_endpoint=None;self._checking_endpoint=False
        self._endpoint=None;self._expected_id=None
        self._group_unsub=None;self._push_version=0
        self._store=Store(controller.hass,1,DOMAIN+".group_endpoint."+controller.entry.entry_id)
    @property
    def connection(self):
        options=self.controller.options
        return self._endpoint or (options.get("group_host",""),options.get("group_port",8099))
    def _seed(self):
        options=self.controller.options
        return [options.get("group_host",""),options.get("group_port",8099),hashlib.sha256(options.get("group_token","").encode()).hexdigest()]
    async def _save_endpoint(self):
        await self._store.async_save({"seed":self._seed(),"id":self._expected_id,"host":self.connection[0],"port":self.connection[1]})
    @callback
    def _address(self,message):
        if not self.enabled:return
        try:key,host,port=endpoint_message(message.payload)
        except (ValueError,TypeError):return
        if self._expected_id and key!=self._expected_id:return
        if self._expected_id==key and (host,port)==self.connection and self.online:return
        self._pending_endpoint=(key,host,port)
        if not self._checking_endpoint:
            self._checking_endpoint=True;self.controller.spawn(self._check_addresses())
    @callback
    def _membership(self,message):
        # Retained messages may predate a restart. Request a fresh, live snapshot.
        if not self.enabled or message.retain or not self.online:return
        try:
            if not isinstance(message.payload,str) or len(message.payload)>16384:return
            value=json.loads(message.payload)
            if not isinstance(value,dict) or value.get("v")!=1 or value.get("self")!=self.data.get("self"):return
            rooms=value.get("panels")
            if not isinstance(rooms,list) or not 1<=len(rooms)<=65:return
            known=self.panels;seen=set();updates={}
            for row in rooms:
                if not isinstance(row,dict):return
                key=row.get("id");leader=row.get("leader")
                if not isinstance(key,str) or key not in known or key in seen:return
                if type(row.get("active")) is not bool or type(row.get("connected")) is not bool:return
                if not isinstance(leader,str) or leader and leader not in known:return
                if row["active"] and not leader:return
                seen.add(key);updates[key]={k:row[k] for k in ("active","leader","connected")}
            if value["self"] not in seen:return
            self.data={**self.data,"panels":[{**row,**updates.get(row["id"],{})} for row in self.data["panels"]]}
            self._push_version+=1;self.controller.notify()
        except (ValueError,TypeError):return
    async def _check_addresses(self):
        try:
            while self._pending_endpoint and not self.controller._closed:
                key,host,port=self._pending_endpoint;self._pending_endpoint=None
                try:
                    async with self._lock:
                        if self._expected_id and key!=self._expected_id:continue
                        await verify_endpoint(self.controller.hass,host,port,key,self.controller.options.get("group_token",""))
                        if self.controller._closed:return
                        self._expected_id=key;self._endpoint=(host,port)
                        await self._save_endpoint()
                    await self.refresh()
                except (aiohttp.ClientError,asyncio.TimeoutError,ValueError,KeyError):
                    _LOGGER.debug("Ignored unverified MQTT group endpoint")
        finally:self._checking_endpoint=False
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
    def _update_device_url(self):
        if not self.controller.device_id:return
        from homeassistant.helpers import device_registry as dr
        registry=dr.async_get(self.controller.hass)
        device=registry.async_get(self.controller.device_id)
        host,port=self.connection
        url=group_url(host,port).removesuffix("api/v1/")
        if device and device.configuration_url!=url:
            registry.async_update_device(device.id,configuration_url=url)
    async def start(self):
        if self.enabled and self._unsub is None:
            saved=await self._store.async_load()
            if isinstance(saved,dict) and saved.get("seed")==self._seed():
                try:
                    key,host,port=endpoint_message(json.dumps({"v":1,**saved}))
                    self._endpoint=(host,port);self._expected_id=key
                except (TypeError,ValueError):pass
            await self.refresh()
            self._mqtt_unsub=await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/app/group_endpoint/state",self._address,1)
            self._group_unsub=await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/app/group_state",self._membership,1)
            await mqtt.async_publish(self.controller.hass,self.controller.base+"/app/group_state/get","",qos=1,retain=False)
            self._unsub=async_track_time_interval(self.controller.hass,lambda _:self.controller.spawn(self.refresh()),timedelta(seconds=5))
    async def stop(self):
        if self._unsub:self._unsub();self._unsub=None
        if self._mqtt_unsub:self._mqtt_unsub();self._mqtt_unsub=None
        if self._group_unsub:self._group_unsub();self._group_unsub=None
        self._pending_endpoint=None
        self.online=False;self._watchers.clear()
    async def refresh(self):
        if not self.enabled or self._lock.locked():return
        async with self._lock:
            options=self.controller.options
            host,port=self.connection
            push_version=self._push_version
            try:
                previous=self.panels
                data=await directory(self.controller.hass,host,port,options.get("group_token",""))
                if push_version!=self._push_version:return
                if self._expected_id and data["self"]!=self._expected_id:raise ValueError("Panel identity changed")
                self.data=data
                if not self._expected_id:
                    self._expected_id=data["self"];await self._save_endpoint()
                self._update_device_url()
                renamed=[p for key,p in self.panels.items() if key in previous and previous[key].get("name")!=p.get("name")]
                if renamed:
                    from homeassistant.helpers import device_registry as dr
                    from .const import DOMAIN
                    registry=dr.async_get(self.controller.hass)
                    for room in renamed:
                        device=registry.async_get_device(identifiers={(DOMAIN,"audio_room:"+room["id"])})
                        if device:registry.async_update_device(device.id,name=room["name"])
                self.online=True;self.error=None;self._discover()
            except (aiohttp.ClientError,asyncio.TimeoutError,ValueError,KeyError) as error:
                if isinstance(error,aiohttp.ClientResponseError) and error.status in (401,403):
                    reason="API token was rejected"
                elif isinstance(error,(ValueError,KeyError)):
                    reason="Invalid group directory response"
                else:reason="Cannot reach the panel; check its current IP address"
                message=f"{reason} ({host}:{port})"
                if self.error!=message:_LOGGER.warning("Audio groups: %s",message)
                self.online=False;self.error=message
            self.controller.notify()
    async def command(self,op,**kwargs):
        if not self.enabled:raise HomeAssistantError("Enable Audio groups in the integration configuration first")
        options=self.controller.options;headers={"Authorization":"Bearer "+options["group_token"]} if options.get("group_token") else {}
        async with self._command_lock:
            host,port=self.connection
            try:
                async with async_get_clientsession(self.controller.hass).post(group_url(host,port)+"group-command",json={"op":op,**kwargs},headers=headers,allow_redirects=False,timeout=aiohttp.ClientTimeout(total=65)) as response:
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
        host,port=self.connection
        try:
            async with async_get_clientsession(self.controller.hass).get(group_url(host,port)+"library",params={"target":node,"folder":folder,"offset":offset,"limit":100},headers=headers,allow_redirects=False,timeout=aiohttp.ClientTimeout(total=10)) as response:
                value=await response.json();response.raise_for_status()
            if value.get("node")!=node or not isinstance(value.get("tracks"),list) or not isinstance(value.get("folders"),list):raise ValueError("Invalid library")
            if value.get("permissionRequired"):raise HomeAssistantError("Allow music access on the source panel in its group settings")
            return value
        except (aiohttp.ClientError,asyncio.TimeoutError,ValueError) as error:raise HomeAssistantError("Cannot browse this panel's local music library") from error
    def room_ids(self,entity_ids):
        lookup={entity:id for id,entity in self.players.items()}
        if any(entity not in lookup for entity in entity_ids):raise HomeAssistantError("Choose Room audio players belonging to this panel directory")
        return [lookup[entity] for entity in entity_ids]
