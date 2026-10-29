"""Existing amplifier hardware level, now owned by the Wall Panel integration."""
from homeassistant.components.number import NumberEntity,NumberMode
from homeassistant.const import EntityCategory
from .hardware import HardwareEntity,SPECS
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([HardwareLevel(entry.runtime_data,s) for s in SPECS if s.domain=="number"]+[DisplaySetting(entry.runtime_data,key,name) for key,name in (("dim_after","Dim after"),("off_after","Display off after"),("dim_brightness","Dim brightness"))]+[ReceiverSetting(entry.runtime_data,key,name) for key,name in (("receiver_delay","AirPlay timing offset"),("receiver_trim","AirPlay trim"))])
    entry.runtime_data.groups.watch("number",async_add_entities,lambda kind,key:RoomTiming(entry.runtime_data,key) if kind=="room" else None)

from .group_entity import GroupEntity
class RoomTiming(GroupEntity,NumberEntity):
    _attr_name="Group timing"
    _attr_native_min_value=-500
    _attr_native_max_value=500
    _attr_native_step=1
    _attr_native_unit_of_measurement="ms"
    _attr_mode=NumberMode.SLIDER
    _attr_entity_category=EntityCategory.CONFIG
    _attr_icon="mdi:timer-sync-outline"
    def __init__(self,controller,key):super().__init__(controller,key,"room","timing")
    @property
    def native_value(self):return self.record.get("roomDelayMs")
    async def async_set_native_value(self,value):await self.groups.command("room",target=self.key,roomDelayMs=int(value))

class ReceiverSetting(KnobEntity,NumberEntity):
    _attr_mode=NumberMode.BOX
    _attr_entity_category=EntityCategory.CONFIG
    _attr_icon="mdi:cast-audio"
    def __init__(self,controller,key,name):
        super().__init__(controller,key);self.key=key;self._attr_name=name
        delay=key=="receiver_delay"
        self._attr_native_min_value=-500 if delay else 0
        self._attr_native_max_value=500 if delay else 100
        self._attr_native_step=5 if delay else 1
        self._attr_native_unit_of_measurement="ms" if delay else "%"
    @property
    def available(self):return self.controller.online and self.controller.audio.online and self.controller.audio.data.get("receiver_supported") is True
    @property
    def native_value(self):return self.controller.audio.data.get(self.key)
    async def async_set_native_value(self,value):await self.controller.audio.command(self.key,value=value)

class HardwareLevel(HardwareEntity,NumberEntity):
    _attr_native_min_value=0
    _attr_native_max_value=100
    _attr_native_step=1
    _attr_native_unit_of_measurement="%"
    _attr_mode=NumberMode.SLIDER
    _attr_entity_category=EntityCategory.CONFIG
    @property
    def native_value(self):return self.hardware_value
    async def async_set_native_value(self,value):await self.send(value)

class DisplaySetting(KnobEntity,NumberEntity):
    _attr_native_step=1
    _attr_mode=NumberMode.BOX
    _attr_entity_category=EntityCategory.CONFIG
    _attr_icon="mdi:monitor"
    def __init__(self,controller,key,name):
        super().__init__(controller,"display_"+key);self.key=key;self._attr_name=name
        self._attr_native_min_value=1 if key=="dim_brightness" else 0
        self._attr_native_max_value=100 if key=="dim_brightness" else 1440
        self._attr_native_unit_of_measurement="%" if key=="dim_brightness" else "min"
    @property
    def available(self):return self.controller.device.display_available and bool(self.controller.device.display_data)
    @property
    def native_value(self):
        value=self.controller.device.display_data.get(self.key)
        return None if value is None else round(value*100/255) if self.key=="dim_brightness" else value/60
    @property
    def extra_state_attributes(self):return {"zero_disables_timer":self.key!="dim_brightness","timer_starts_from":"last screen interaction"}
    async def async_set_native_value(self,value):await self.controller.device.policy(self.key,value)
