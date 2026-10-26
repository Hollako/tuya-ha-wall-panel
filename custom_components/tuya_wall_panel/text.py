"""Dashboard configuration; knob text is managed by the background controller."""
from homeassistant.components.text import TextEntity,TextMode
from homeassistant.const import EntityCategory
from homeassistant.exceptions import HomeAssistantError
from .hardware import HardwareEntity,SPECS,TEXT_PATTERN
from .entity import KnobEntity
from .dashboard import validate_dashboard_url
from .const import DOMAIN

KNOB_TEXT_KEYS={"knob_display_title","knob_display_value","knob_display_detail"}

async def async_setup_entry(hass,entry,async_add_entities):
    from homeassistant.helpers import entity_registry as er
    # Retire only this entry's three manual text entities. MQTT state tracking
    # and the controller's independent frame publishing remain active.
    registry=er.async_get(hass)
    ident=entry.runtime_data.base.rsplit("/",1)[-1]
    for key in KNOB_TEXT_KEYS:
        entity_id=registry.async_get_entity_id("text",DOMAIN,ident+"_"+key)
        entity=registry.async_get(entity_id) if entity_id else None
        if entity and entity.config_entry_id==entry.entry_id:
            registry.async_remove(entity_id)
    async_add_entities([PanelText(entry.runtime_data,s) for s in SPECS if s.domain=="text" and s.key not in KNOB_TEXT_KEYS]+[DashboardURL(entry.runtime_data)])

class PanelText(HardwareEntity,TextEntity):
    _attr_mode=TextMode.TEXT
    _attr_native_min=0
    _attr_pattern=TEXT_PATTERN
    def __init__(self,controller,spec):
        super().__init__(controller,spec);self._attr_native_max=spec.maximum
        self._attr_entity_registry_visible_default=spec.key not in KNOB_TEXT_KEYS
    @property
    def native_value(self):return self.hardware_value
    async def async_set_value(self,value):await self.send(value)

class DashboardURL(KnobEntity,TextEntity):
    _attr_name="Dashboard URL"
    _attr_icon="mdi:web"
    _attr_entity_category=EntityCategory.CONFIG
    _attr_mode=TextMode.TEXT
    _attr_native_min=0
    _attr_native_max=255
    @property
    def extra_state_attributes(self):
        app=self.controller.app
        return {"delivery":"mqtt","applied_to_panel":app.online and app.dashboard==self.native_value and app.dashboard_error is None,
                "panel_url":app.dashboard,"delivery_error":app.dashboard_error}
    def __init__(self,controller):super().__init__(controller,"dashboard_url")
    @property
    def available(self):return True # HA-side settings stay editable while the panel is offline.
    @property
    def native_value(self):return self.controller.options.get("dashboard_url","")
    async def async_set_value(self,value):
        try:value=validate_dashboard_url(value)
        except ValueError as err:raise HomeAssistantError(str(err)) from err
        c=self.controller
        c.hass.config_entries.async_update_entry(c.entry,options={**c.entry.options,"dashboard_url":value,"dashboard_managed":True})
        c.options["dashboard_url"]=value;c.options["dashboard_managed"]=True
        c.notify()
        await c.app.send_dashboard()
