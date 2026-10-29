"""Dynamic rooms belong to their physical panel, saved groups to the controller."""
from homeassistant.helpers.entity import Entity,DeviceInfo
from .const import DOMAIN

class GroupEntity(Entity):
    _attr_should_poll=False
    _attr_has_entity_name=True
    def __init__(self,controller,key,kind,suffix):
        self.controller=controller;self.groups=controller.groups;self.key=key;self.kind=kind
        self._attr_unique_id=controller.entry.entry_id+"_group_"+kind+"_"+key+"_"+suffix
        name=(self.groups.panels if kind=="room" else self.groups.presets).get(key,{}).get("name","Wall Panel audio group")
        self._attr_device_info=DeviceInfo(identifiers={(DOMAIN,"audio_room:"+key)},name=name,manufacturer="Tuya Wall Panel",model="Network audio room") if kind=="room" else DeviceInfo(identifiers={(DOMAIN,controller.base)})
    @property
    def record(self):return (self.groups.panels if self.kind=="room" else self.groups.presets).get(self.key,{})
    @property
    def available(self):
        if not self.groups.online:return False
        node=self.record if self.kind=="room" else self.groups.panels.get(self.record.get("leader"),{})
        return bool(self.record and node.get("online") and node.get("authorized"))
    async def async_added_to_hass(self):
        await super().async_added_to_hass()
        self.async_on_remove(self.controller.add_listener(self.async_write_ha_state))
