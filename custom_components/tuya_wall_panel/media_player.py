"""The panel's Android player and local Line Input source."""
from homeassistant.components.media_player import MediaPlayerEntity,MediaPlayerEntityFeature,MediaPlayerState,MediaPlayerDeviceClass,async_process_play_media_url
from homeassistant.components import media_source
from homeassistant.exceptions import HomeAssistantError
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([PanelPlayer(entry.runtime_data)])

class PanelPlayer(KnobEntity,MediaPlayerEntity):
    _attr_name="Audio"
    _attr_icon="mdi:speaker"
    _attr_device_class=MediaPlayerDeviceClass.SPEAKER
    _attr_source_list=["Android playback","Line Input"]
    _attr_supported_features=(MediaPlayerEntityFeature.TURN_ON|MediaPlayerEntityFeature.TURN_OFF|
        MediaPlayerEntityFeature.PLAY|MediaPlayerEntityFeature.PAUSE|MediaPlayerEntityFeature.STOP|
        MediaPlayerEntityFeature.VOLUME_SET|MediaPlayerEntityFeature.VOLUME_STEP|MediaPlayerEntityFeature.VOLUME_MUTE|
        MediaPlayerEntityFeature.SELECT_SOURCE|MediaPlayerEntityFeature.PLAY_MEDIA|MediaPlayerEntityFeature.BROWSE_MEDIA)

    def __init__(self,controller):
        super().__init__(controller,"audio_player");self.audio=controller.audio

    @property
    def available(self):return self.controller.online and self.audio.online
    @property
    def state(self):return MediaPlayerState(self.audio.data.get("state","idle"))
    @property
    def volume_level(self):return self.audio.data.get("volume")
    @property
    def is_volume_muted(self):return self.audio.data.get("muted")
    @property
    def source(self):return self.audio.data.get("source")
    @property
    def source_list(self):return self.audio.data.get("sources") or self._attr_source_list
    @property
    def media_artist(self):return self.audio.data.get("artist")
    @property
    def media_album_name(self):return self.audio.data.get("album")
    @property
    def media_title(self):return self.audio.data.get("title") or ("Line Input" if self.source=="Line Input" else None)
    @property
    def media_duration(self):return self.audio.data.get("duration")
    @property
    def media_position(self):return self.audio.data.get("position")
    @property
    def media_position_updated_at(self):return self.audio.position_updated_at
    @property
    def extra_state_attributes(self):return {"audio_error":self.audio.data.get("error") or None,
        "external_speakers":self.audio.data.get("external_speakers"),"volume_scope":self.audio.data.get("volume_scope","Android playback"),
        "receiver_state":self.audio.data.get("receiver_state"),"transport_control":self.audio.data.get("transport_control"),
        "line_input_recording_saved":False}

    async def async_turn_on(self):await self.audio.command("on")
    async def async_turn_off(self):await self.audio.command("off")
    async def async_media_play(self):await self.audio.command("play")
    async def async_media_pause(self):await self.audio.command("pause")
    async def async_media_stop(self):await self.audio.command("stop")
    async def async_set_volume_level(self,volume):await self.audio.command("volume",value=volume)
    async def async_mute_volume(self,mute):await self.audio.command("mute",value=mute)
    async def async_select_source(self,source):
        if source not in self.source_list:raise HomeAssistantError("Choose an available audio source")
        await self.audio.command("source",value=source)

    async def async_play_media(self,media_type,media_id,**kwargs):
        if kwargs.get("announce") or kwargs.get("enqueue") not in (None,"replace"):
            raise HomeAssistantError("Announcement restore and playback queues are not supported")
        if media_source.is_media_source_id(media_id):
            item=await media_source.async_resolve_media(self.hass,media_id,self.entity_id);media_id=item.url
        media_id=async_process_play_media_url(self.hass,media_id)
        if not media_id.startswith(("http://","https://")):raise HomeAssistantError("Use an HTTP/HTTPS audio URL or HA media source")
        await self.audio.command("play_url",url=media_id,title=(kwargs.get("extra") or {}).get("title","Home Assistant audio"))

    async def async_browse_media(self,media_content_type=None,media_content_id=None):
        return await media_source.async_browse_media(self.hass,media_content_id,content_filter=lambda item:item.media_content_type.startswith("audio/"))
