"""Shared callback-driven controller entity."""
from homeassistant.helpers.entity import Entity,DeviceInfo
from .const import DOMAIN

class KnobEntity(Entity):
    _attr_should_poll=False
    _attr_has_entity_name=True
    def __init__(self,controller,key):
        self.controller=controller
        self._attr_unique_id=controller.entry.entry_id+"_"+key
        self._attr_device_info=DeviceInfo(identifiers={(DOMAIN,controller.base)},name="Tuya Wall Panel",manufacturer="Sznaner",model="G10 / PX30 Android wall panel")

    @property
    def available(self):return self.controller.online

    async def async_added_to_hass(self):
        await super().async_added_to_hass()
        self.async_on_remove(self.controller.add_listener(self.async_write_ha_state))
