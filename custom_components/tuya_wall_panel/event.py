"""Four independent button event entities and one independent knob entity."""
from homeassistant.components.event import EventEntity
from .hardware import HardwareEntity,HardwareSpec,EVENTS

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([PanelEvent(entry.runtime_data,key) for key in EVENTS])

class PanelEvent(HardwareEntity,EventEntity):
    def __init__(self,controller,key):
        spec=HardwareSpec("event",key,"Knob" if key=="knob" else "Button "+key[-1],"", "knob_input" if key=="knob" else "module",icon="mdi:knob" if key=="knob" else "mdi:gesture-tap-button")
        super().__init__(controller,spec)
        self._attr_event_types=list(EVENTS[key])
    async def async_added_to_hass(self):
        await super().async_added_to_hass()
        self.async_on_remove(self.controller.hardware.listen(self.spec.key,self._received))
    def _received(self,data):
        self._trigger_event(data["event_type"],{k:v for k,v in data.items() if k!="event_type"})
        self.async_write_ha_state()
