"""Expose selection and menu state for dashboards and other HA automations."""
from homeassistant.components.sensor import SensorEntity
from homeassistant.const import EntityCategory
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([ControlStatus(entry.runtime_data)])

class ControlStatus(KnobEntity,SensorEntity):
    _attr_name="Knob controller status"
    _attr_icon="mdi:knob"
    _attr_entity_category=EntityCategory.DIAGNOSTIC
    def __init__(self,controller):super().__init__(controller,"status")

    @property
    def native_value(self):
        c=self.controller
        return "disabled" if not c.enabled else "selecting" if c.menu.cursor is not None else "active" if c.menu.active else "unconfigured"

    @property
    def extra_state_attributes(self):
        c=self.controller
        return {"active_entity":c.menu.active,"configured_entities":c.options["entities"],"clock_enabled":c.options["include_clock"],
                "menu_highlight":c.targets[c.menu.cursor] if c.menu.cursor is not None else None,"last_error":c.error}
