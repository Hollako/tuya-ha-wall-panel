"""Disable higher-level control without removing the user's selected entity list."""
from homeassistant.components.switch import SwitchEntity
from .entity import KnobEntity
from .hardware import HardwareEntity,SPECS,HardwareSpec

async def async_setup_entry(hass,entry,async_add_entities):
    c=entry.runtime_data
    async_add_entities([ControllerEnabled(c),*(PanelSwitch(c,s) for s in SPECS if s.domain=="switch"),ExternalSpeakers(c)])

class PanelSwitch(HardwareEntity,SwitchEntity):
    @property
    def is_on(self):
        value=self.hardware_value
        return None if value is None else value=="ON"
    async def async_turn_on(self,**kwargs):await self.send("ON")
    async def async_turn_off(self,**kwargs):await self.send("OFF")

class ExternalSpeakers(HardwareEntity,SwitchEntity):
    def __init__(self,controller):
        super().__init__(controller,HardwareSpec("switch","audio_external_speakers","External Speakers","",icon="mdi:speaker-multiple"))
    @property
    def available(self):return self.controller.online and self.controller.audio.online
    @property
    def is_on(self):return self.controller.audio.data.get("external_speakers")
    async def async_turn_on(self,**kwargs):await self.controller.audio.command("external_speakers",value=True)
    async def async_turn_off(self,**kwargs):await self.controller.audio.command("external_speakers",value=False)

class ControllerEnabled(KnobEntity,SwitchEntity):
    _attr_name="Knob entity control"
    _attr_icon="mdi:knob"
    def __init__(self,controller):super().__init__(controller,"enabled")

    @property
    def is_on(self):return self.controller.enabled

    async def async_turn_on(self,**kwargs):await self.controller.set_enabled(True)
    async def async_turn_off(self,**kwargs):await self.controller.set_enabled(False)
