"""The main backlight; Android stays awake when this light is off."""
from homeassistant.components.light import ATTR_BRIGHTNESS,ColorMode,LightEntity
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    async_add_entities([MainDisplay(entry.runtime_data)])

class MainDisplay(KnobEntity,LightEntity):
    _attr_name="Main display"
    _attr_icon="mdi:monitor"
    _attr_supported_color_modes={ColorMode.BRIGHTNESS}
    _attr_color_mode=ColorMode.BRIGHTNESS

    @property
    def available(self):return self.controller.device.display_available
    @property
    def is_on(self):return self.controller.device.data.get("display_on",False)
    @property
    def brightness(self):return self.controller.device.data.get("brightness")
    @property
    def extra_state_attributes(self):
        data=self.controller.device.data
        return {"phase":data.get("phase"),"actual_brightness":data.get("actual_brightness"),"brightness_represents":"normal brightness; dim level is separate"}

    def __init__(self,controller):super().__init__(controller,"main_display")

    async def async_turn_on(self,**kwargs):
        if ATTR_BRIGHTNESS in kwargs:
            if kwargs[ATTR_BRIGHTNESS]==0:
                await self.async_turn_off();return
            await self.controller.device.brightness(kwargs[ATTR_BRIGHTNESS])
        await self.controller.device.command("DISPLAY_ON")

    async def async_turn_off(self,**kwargs):await self.controller.device.command("DISPLAY_OFF")
