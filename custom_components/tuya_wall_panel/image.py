"""Cached screen image, updated every five minutes or by Refresh screenshot."""
from homeassistant.components.image import ImageEntity
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([PanelScreenshot(entry.runtime_data)])

class PanelScreenshot(KnobEntity,ImageEntity):
    _attr_name="Screen screenshot"
    _attr_icon="mdi:monitor-screenshot"
    _attr_content_type="image/png"

    def __init__(self,controller):
        KnobEntity.__init__(self,controller,"screen_screenshot")
        ImageEntity.__init__(self,controller.hass)

    @property
    def available(self):return self.controller.device.available and self.controller.device.image is not None
    @property
    def image_last_updated(self):return self.controller.device.image_updated
    @property
    def extra_state_attributes(self):
        return {"refresh_interval_seconds":300,"display_on":self.controller.device.data.get("display_on"),
                "panel_error":self.controller.device.data.get("error") or None}

    async def async_image(self):return self.controller.device.image
