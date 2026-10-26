"""Existing amplifier hardware level, now owned by the Wall Panel integration."""
from homeassistant.components.number import NumberEntity,NumberMode
from homeassistant.const import EntityCategory
from .hardware import HardwareEntity,SPECS
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([HardwareLevel(entry.runtime_data,s) for s in SPECS if s.domain=="number"]+[DisplaySetting(entry.runtime_data,key,name) for key,name in (("dim_after","Dim after"),("off_after","Display off after"),("dim_brightness","Dim brightness"))])

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
