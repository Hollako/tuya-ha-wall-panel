"""The panel's Android player and local Line Input source."""
import re
from homeassistant.components.media_player import MediaPlayerEntity,MediaPlayerEntityFeature,MediaPlayerState,MediaPlayerDeviceClass,async_process_play_media_url
from homeassistant.components import media_source
from homeassistant.exceptions import HomeAssistantError
from .entity import KnobEntity
from .audio import LOCAL_PLAYBACK,LEGACY_LOCAL_PLAYBACK,source_name

async def async_setup_entry(hass,entry,async_add_entities):
    controller=entry.runtime_data
    from homeassistant.helpers import entity_registry as er
    from .const import DOMAIN
    registry=er.async_get(hass)
    prefix=entry.entry_id+"_group_preset_"
    for entity in er.async_entries_for_config_entry(registry,entry.entry_id):
        if entity.domain=="media_player" and entity.platform==DOMAIN and entity.unique_id.startswith(prefix) and re.fullmatch(r"group-[A-Za-z0-9-]{8,80}_player",entity.unique_id[len(prefix):]):
            registry.async_remove(entity.entity_id)
    # Retire only this directory's local room duplicate. Keep the original Audio
    # unique/entity ID so dashboards, automations and local knob routing survive.
    def build(kind,key):
        from .group_player import RoomPlayer
        if kind!="room":return None
        if key!=controller.groups.data.get("self"):return RoomPlayer(controller,key)
        from homeassistant.helpers import entity_registry as er
        registry=er.async_get(hass)
        from .const import DOMAIN
        duplicate=registry.async_get_entity_id("media_player",DOMAIN,entry.entry_id+"_group_room_"+key+"_player")
        if duplicate and registry.async_get(duplicate).config_entry_id==entry.entry_id:
            registry.async_remove(duplicate)
        return None
    async_add_entities([PanelPlayer(controller)])
    controller.groups.watch("media_player",async_add_entities,build)

class PanelPlayer(KnobEntity,MediaPlayerEntity):
    _attr_name="Audio"
    _attr_icon="mdi:speaker"
    _attr_device_class=MediaPlayerDeviceClass.SPEAKER
    _attr_media_image_remotely_accessible=False
    _attr_volume_step=.01
    _attr_source_list=[LOCAL_PLAYBACK,"Line Input"]
    _attr_supported_features=(MediaPlayerEntityFeature.TURN_ON|MediaPlayerEntityFeature.TURN_OFF|
        MediaPlayerEntityFeature.PLAY|MediaPlayerEntityFeature.PAUSE|MediaPlayerEntityFeature.STOP|
        MediaPlayerEntityFeature.NEXT_TRACK|MediaPlayerEntityFeature.PREVIOUS_TRACK|
        MediaPlayerEntityFeature.VOLUME_SET|MediaPlayerEntityFeature.VOLUME_STEP|MediaPlayerEntityFeature.VOLUME_MUTE|
        MediaPlayerEntityFeature.SELECT_SOURCE|MediaPlayerEntityFeature.PLAY_MEDIA|MediaPlayerEntityFeature.BROWSE_MEDIA|MediaPlayerEntityFeature.GROUPING)

    def __init__(self,controller):
        super().__init__(controller,"audio_player");self.audio=controller.audio;self.groups=controller.groups

    async def async_added_to_hass(self):
        await super().async_added_to_hass()
        self._register_room()
        self.async_on_remove(self.controller.add_listener(self._register_room))
        self.async_on_remove(self._unregister_room)

    def _register_room(self):
        key=self.groups.data.get("self")
        if key:self.groups.players[key]=self.entity_id

    def _unregister_room(self):
        for key,value in list(self.groups.players.items()):
            if value==self.entity_id:self.groups.players.pop(key,None)

    @property
    def room(self):
        from .group_player import RoomPlayer
        key=self.groups.data.get("self")
        if not key or not self.groups.online:return None
        room=RoomPlayer(self.controller,key);room.hass=self.hass;room.entity_id=self.entity_id
        return room if room.available else None

    @property
    def grouped(self):return self.room is not None and bool(self.room.record.get("active"))

    @property
    def room_transport(self):
        return self.room is not None and (self.grouped or self.room.record.get("nowPlaying",{}).get("kind") in ("bluetooth","apps","airplay","local"))

    @property
    def group_members(self):return self.room.group_members if self.room else []

    @property
    def saved_groups(self):
        if not self.groups.online:return {}
        presets=self.groups.presets
        counts={p.get("name","Saved group"):sum(other.get("name","Saved group")==p.get("name","Saved group") for other in presets.values()) for p in presets.values()}
        return {"Group: "+p.get("name","Saved group")+(" ("+key+")" if counts[p.get("name","Saved group")]>1 else ""):key for key,p in presets.items()}

    async def async_join_players(self,group_members):
        if not self.room:raise HomeAssistantError("Audio groups connection is unavailable; check the panel address in Configure > Audio groups connection")
        await self.room.async_join_players(group_members)

    async def async_unjoin_player(self):
        if not self.room:raise HomeAssistantError("Audio groups connection is unavailable")
        await self.room.async_unjoin_player()

    @property
    def available(self):return (self.controller.online and self.audio.online) or self.room is not None
    @property
    def state(self):return self.room.state if self.room_transport or not self.audio.online and self.room else MediaPlayerState(self.audio.data.get("state","idle"))
    @property
    def volume_level(self):return self.room.volume_level if self.room else self.audio.data.get("volume")
    @property
    def is_volume_muted(self):return self.room.is_volume_muted if self.room else self.audio.data.get("muted")
    @property
    def source(self):return self.room.source if self.room_transport else self.audio.data.get("source") or (self.room.source if self.room else None)
    @property
    def source_list(self):return list(dict.fromkeys((self.audio.data.get("sources") or self._attr_source_list)+(self.room.source_list if self.room else [])+list(self.saved_groups)))
    @property
    def media_artist(self):return None if self.grouped else self.audio.data.get("artist")
    @property
    def media_album_name(self):return None if self.grouped else self.audio.data.get("album")
    @property
    def media_image_hash(self):return self.audio.artwork_id if self.available and not self.grouped and self.audio.current_artwork else None
    async def async_get_media_image(self):
        picture=self.audio.current_artwork if self.available and not self.grouped else None
        return (picture,"image/jpeg") if picture else (None,None)
    @property
    def media_title(self):return self.room.media_title if self.room_transport else self.audio.data.get("title") or ("Line Input" if self.source=="Line Input" else None)
    @property
    def media_duration(self):return None if self.grouped else self.audio.data.get("duration")
    @property
    def media_position(self):return None if self.grouped else self.audio.data.get("position")
    @property
    def media_position_updated_at(self):return self.audio.position_updated_at
    @property
    def extra_state_attributes(self):return {"audio_error":self.audio.data.get("error") or None,
        "external_speakers":self.audio.data.get("external_speakers"),"volume_scope":self.audio.data.get("volume_scope",LOCAL_PLAYBACK),
        "receiver_state":self.audio.data.get("receiver_state"),"transport_control":self.audio.data.get("transport_control"),
        "line_input_recording_saved":False,"group_connection_error":self.groups.error,
        "group_api_host":self.groups.connection[0],"group_api_port":self.groups.connection[1],
        **(self.room.extra_state_attributes if self.room else {})}

    async def async_turn_on(self):await self.audio.command("on")
    async def async_turn_off(self):await self.audio.command("off")
    async def async_media_play(self):
        if self.room_transport:await self.room.async_media_play()
        else:await self.audio.command("play")
    async def async_media_pause(self):
        if self.room_transport:await self.room.async_media_pause()
        else:await self.audio.command("pause")
    async def async_media_stop(self):
        if self.grouped:await self.room.async_media_stop()
        else:await self.audio.command("stop")
    async def async_media_next_track(self):
        if self.room_transport:await self.room.async_media_next_track()
        else:await self.audio.command("next")
    async def async_media_previous_track(self):
        if self.room_transport:await self.room.async_media_previous_track()
        else:await self.audio.command("previous")
    async def async_set_volume_level(self,volume):
        if self.room:await self.room.async_set_volume_level(volume)
        else:await self.audio.command("volume",value=volume)
    async def async_mute_volume(self,mute):
        if self.room:await self.room.async_mute_volume(mute)
        else:await self.audio.command("mute",value=mute)
    async def async_select_source(self,source):
        if source in self.saved_groups:
            await self.groups.command("activate",id=self.saved_groups[source]);return
        source=source_name(source)
        if source not in self.source_list:raise HomeAssistantError("Choose an available audio source")
        if self.room and source in self.room.source_list:
            await self.room.async_select_source(source);return
        if self.grouped:await self.room.async_unjoin_player()
        await self.audio.command("source",value=LEGACY_LOCAL_PLAYBACK if source==LOCAL_PLAYBACK else source)

    async def async_play_media(self,media_type,media_id,**kwargs):
        if self.room:
            await self.room.async_play_media(media_type,media_id,**kwargs);return
        if kwargs.get("announce") or kwargs.get("enqueue") not in (None,"replace"):
            raise HomeAssistantError("Announcement restore and playback queues are not supported")
        if media_source.is_media_source_id(media_id):
            item=await media_source.async_resolve_media(self.hass,media_id,self.entity_id);media_id=item.url
        media_id=async_process_play_media_url(self.hass,media_id)
        if not media_id.startswith(("http://","https://")):raise HomeAssistantError("Use an HTTP/HTTPS audio URL or HA media source")
        await self.audio.command("play_url",url=media_id,title=(kwargs.get("extra") or {}).get("title","Home Assistant audio"))

    async def async_browse_media(self,media_content_type=None,media_content_id=None):
        if self.room:return await self.room.async_browse_media(media_content_type,media_content_id)
        return await media_source.async_browse_media(self.hass,media_content_id,content_filter=lambda item:item.media_content_type.startswith("audio/"))
