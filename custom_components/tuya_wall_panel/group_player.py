"""Standard HA join/unjoin and saved group media players."""
import asyncio
import math
from homeassistant.components.media_player import MediaPlayerEntity,MediaPlayerEntityFeature,MediaPlayerState,MediaPlayerDeviceClass,async_process_play_media_url
from homeassistant.components import media_source
from homeassistant.exceptions import HomeAssistantError
from .group_entity import GroupEntity

FEATURES=(MediaPlayerEntityFeature.PLAY|MediaPlayerEntityFeature.PAUSE|MediaPlayerEntityFeature.STOP|
    MediaPlayerEntityFeature.VOLUME_SET|MediaPlayerEntityFeature.VOLUME_MUTE|MediaPlayerEntityFeature.VOLUME_STEP)
def whole_volume(value):return math.floor(value*100+.5)/100

class RoomPlayer(GroupEntity,MediaPlayerEntity):
    _attr_volume_step=.01
    _attr_name="Room audio"
    _attr_device_class=MediaPlayerDeviceClass.SPEAKER
    _attr_supported_features=FEATURES|MediaPlayerEntityFeature.GROUPING|MediaPlayerEntityFeature.PLAY_MEDIA|MediaPlayerEntityFeature.BROWSE_MEDIA|MediaPlayerEntityFeature.SELECT_SOURCE
    def __init__(self,controller,key):super().__init__(controller,key,"room","player")
    async def async_added_to_hass(self):
        await super().async_added_to_hass();self.groups.players[self.key]=self.entity_id
        self.async_on_remove(lambda:self.groups.players.pop(self.key,None))
    @property
    def leader(self):return self.record.get("leader") or self.key
    @property
    def state(self):return MediaPlayerState(self.record.get("state","idle"))
    @property
    def volume_level(self):return whole_volume(self.record["volume"]) if self.record.get("volume") is not None else None
    @property
    def is_volume_muted(self):return self.record.get("muted")
    @property
    def group_members(self):
        keys=self.groups.members(self.leader)
        return [self.groups.players[k] for k in [self.leader]+[k for k in keys if k!=self.leader] if k in keys and k in self.groups.players]
    @property
    def media_title(self):return self.groups.panels.get(self.leader,{}).get("sourceTitle") or ("Group source: "+self.groups.panels.get(self.leader,{}).get("name","this room") if self.record.get("active") else None)
    @property
    def extra_state_attributes(self):return {key:self.record.get(key) for key in ("id","leader","role","connected","status","roomDelayMs","sourceConfigured","sourceKind","bluetoothGroupAvailable","captureStatus")}
    @property
    def source(self):return "Group stream" if self.record.get("active") and self.leader!=self.key else "Bluetooth" if self.record.get("sourceKind")=="bluetooth" else "Local music"
    @property
    def source_list(self):return ["Local music"]+(["Bluetooth"] if self.record.get("bluetoothGroupAvailable") else [])+(["Group stream"] if self.record.get("active") and self.leader!=self.key else [])
    async def async_select_source(self,source):
        if source=="Group stream" and self.source==source:return
        if source not in ("Local music","Bluetooth"):raise HomeAssistantError("Choose an available group input")
        await self.groups.command("group_input",leader=self.key,input="bluetooth" if source=="Bluetooth" else "file",members=self.groups.members(self.leader) or [self.key])
    async def async_set_volume_level(self,volume):await self.groups.command("room",target=self.key,volume=whole_volume(volume))
    async def async_mute_volume(self,mute):await self.groups.command("room",target=self.key,muted=mute)
    async def async_media_play(self):
        if self.record.get("active"):await self.groups.command("transport",leader=self.leader,command="play")
        else:await self.groups.command("set_group",leader=self.key,members=[self.key])
    async def async_media_pause(self):await self.groups.command("transport",leader=self.leader,command="pause")
    async def async_media_stop(self):await self.groups.command("leave",target=self.key)
    async def async_join_players(self,group_members):
        members=list(dict.fromkeys(self.groups.members(self.leader)+[self.leader]+self.groups.room_ids(group_members)))
        await self.groups.command("set_group",leader=self.leader,members=members)
    async def async_unjoin_player(self):await self.groups.command("leave",target=self.key)
    async def async_play_media(self,media_type,media_id,**kwargs):
        from .group_library import play
        leader=self.key if media_id.startswith("wallpanel-local:") else self.leader
        await play(self,leader,self.groups.members(self.leader) or [self.key],media_id,kwargs)
    async def async_browse_media(self,media_content_type=None,media_content_id=None):
        from .group_library import browse
        return await browse(self,self.key,media_content_id)

class SavedGroupPlayer(GroupEntity,MediaPlayerEntity):
    _attr_volume_step=.01
    _attr_device_class=MediaPlayerDeviceClass.SPEAKER
    _attr_icon="mdi:speaker-multiple"
    _attr_supported_features=FEATURES|MediaPlayerEntityFeature.TURN_ON|MediaPlayerEntityFeature.TURN_OFF|MediaPlayerEntityFeature.PLAY_MEDIA|MediaPlayerEntityFeature.BROWSE_MEDIA|MediaPlayerEntityFeature.SELECT_SOURCE
    def __init__(self,controller,key):
        super().__init__(controller,key,"preset","player");self._attr_name=self.record.get("name","Saved audio group")
    @property
    def name(self):return self.record.get("name",self._attr_name)
    @property
    def active(self):return bool(self.record and set(self.record["members"])==set(self.groups.members(self.record["leader"])))
    @property
    def state(self):return MediaPlayerState(self.groups.panels.get(self.record.get("leader"),{}).get("state","idle")) if self.active else MediaPlayerState.IDLE
    @property
    def volume_level(self):
        levels=[self.groups.panels[k]["volume"] for k in self.record.get("members",[]) if isinstance(self.groups.panels.get(k,{}).get("volume"),(float,int))]
        return whole_volume(sum(levels)/len(levels)) if levels else None
    @property
    def is_volume_muted(self):return all(self.groups.panels.get(k,{}).get("muted",False) for k in self.record.get("members",[]))
    @property
    def extra_state_attributes(self):return {"leader":self.record.get("leader"),"rooms":self.record.get("members",[]),"active":self.active,"unavailable_rooms":[k for k in self.record.get("members",[]) if not self.groups.panels.get(k,{}).get("online")]}
    @property
    def group_members(self):return [self.groups.players[k] for k in self.record.get("members",[]) if k in self.groups.players] if self.active else []
    def require_active(self):
        if not self.active:raise HomeAssistantError("Start this saved group first")
    async def async_turn_on(self):await self.async_media_play()
    async def async_turn_off(self):await self.async_media_stop()
    async def async_media_play(self):
        if self.active:await self.groups.command("transport",leader=self.record["leader"],command="play")
        else:await self.groups.command("activate",id=self.key)
    async def async_media_pause(self):self.require_active();await self.groups.command("transport",leader=self.record["leader"],command="pause")
    async def async_media_stop(self):self.require_active();await self.groups.command("transport",leader=self.record["leader"],command="stop")
    async def async_set_volume_level(self,volume):self.require_active();await self.groups.command("group_volume",leader=self.record["leader"],volume=whole_volume(volume))
    async def async_mute_volume(self,mute):self.require_active();await self.groups.command("group_mute",leader=self.record["leader"],muted=mute)
    @property
    def media_title(self):return self.groups.panels.get(self.record.get("leader"),{}).get("sourceTitle") if self.active else None
    async def async_browse_media(self,media_content_type=None,media_content_id=None):
        from .group_library import browse
        return await browse(self,self.record["leader"],media_content_id)
    async def async_play_media(self,media_type,media_id,**kwargs):
        from .group_library import play
        await play(self,self.record["leader"],self.record["members"],media_id,kwargs)
    @property
    def source(self):return "Bluetooth" if self.groups.panels.get(self.record.get("leader"),{}).get("sourceKind")=="bluetooth" else "Local music"
    @property
    def source_list(self):return ["Local music"]+(["Bluetooth"] if self.groups.panels.get(self.record.get("leader"),{}).get("bluetoothGroupAvailable") else [])
    async def async_select_source(self,source):
        if source not in ("Local music","Bluetooth"):raise HomeAssistantError("Choose Local music or Bluetooth")
        await self.groups.command("group_input",leader=self.record["leader"],input="bluetooth" if source=="Bluetooth" else "file",members=self.record["members"])
