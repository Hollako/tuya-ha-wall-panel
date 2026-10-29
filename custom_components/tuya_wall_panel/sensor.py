"""Expose selection and menu state for dashboards and other HA automations."""
from homeassistant.components.sensor import SensorEntity
from homeassistant.const import EntityCategory
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([ControlStatus(entry.runtime_data)])
    if entry.runtime_data.groups.enabled:async_add_entities([GroupDirectory(entry.runtime_data)])
    entry.runtime_data.groups.watch("sensor",async_add_entities,lambda kind,key:RoomConnection(entry.runtime_data,key) if kind=="room" else None)

from .group_entity import GroupEntity
class RoomConnection(GroupEntity,SensorEntity):
    _attr_name="Group connection"
    _attr_icon="mdi:lan-connect"
    _attr_entity_category=EntityCategory.DIAGNOSTIC
    def __init__(self,controller,key):super().__init__(controller,key,"room","connection")
    @property
    def native_value(self):return "connected" if self.record.get("connected") else "connecting" if self.record.get("active") else "idle"
    @property
    def extra_state_attributes(self):return {key:self.record.get(key) for key in ("leader","role","state","status","online","authorized")}

class GroupDirectory(KnobEntity,SensorEntity):
    _attr_name="Audio rooms"
    _attr_icon="mdi:speaker-multiple"
    _attr_entity_category=EntityCategory.DIAGNOSTIC
    def __init__(self,controller):super().__init__(controller,"audio_rooms")
    @property
    def available(self):return self.controller.groups.online
    @property
    def native_value(self):return sum(p.get("online",False) for p in self.controller.groups.panels.values())
    @property
    def extra_state_attributes(self):
        groups=self.controller.groups
        return {"panels":[{k:p.get(k) for k in ("id","name","online","authorized","leader","active")} for p in groups.panels.values()],"saved_groups":list(groups.presets.values()),"operation":groups.data.get("operation"),"last_error":groups.error}

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
