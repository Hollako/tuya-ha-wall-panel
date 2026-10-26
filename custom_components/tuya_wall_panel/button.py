"""HA controls for next entity and selection menu."""
from homeassistant.components.button import ButtonEntity,ButtonDeviceClass
from homeassistant.const import EntityCategory
from .entity import KnobEntity

async def async_setup_entry(hass,entry,async_add_entities):
    c=entry.runtime_data
    async_add_entities([Navigation(c,"next","Knob next entity","double_press"),Navigation(c,"menu","Knob selection menu","long_press"),
        PanelAction(c,"reboot","Reboot","REBOOT","mdi:restart",EntityCategory.CONFIG),
        PanelAction(c,"display_off","Turn display off","DISPLAY_OFF","mdi:monitor-off"),
        PanelAction(c,"display_wake","Wake display","DISPLAY_ON","mdi:monitor"),
        PanelAction(c,"screenshot_refresh","Refresh screenshot","SCREENSHOT","mdi:camera-retake",EntityCategory.DIAGNOSTIC),OpenSetup(c),
        AppAction(c,"reload_dashboard","Reload dashboard","RELOAD_DASHBOARD","mdi:reload"),
        AppAction(c,"restart_app","Restart app","RESTART_APP","mdi:restart")])

class Navigation(KnobEntity,ButtonEntity):
    _attr_icon="mdi:gesture-tap-button"
    def __init__(self,controller,key,name,event):
        super().__init__(controller,key);self._attr_name=name;self.event=event

    async def async_press(self):await self.controller.gesture(self.event)

class OpenSetup(KnobEntity,ButtonEntity):
    _attr_name="Open setup"
    _attr_icon="mdi:cog"
    _attr_entity_category=EntityCategory.CONFIG
    def __init__(self,controller):super().__init__(controller,"open_setup")
    @property
    def available(self):return self.controller.app.online
    async def async_press(self):await self.controller.app.open_setup()

class AppAction(KnobEntity,ButtonEntity):
    _attr_entity_category=EntityCategory.CONFIG
    def __init__(self,controller,key,name,command,icon):
        super().__init__(controller,key);self._attr_name=name;self.command=command;self._attr_icon=icon
        if command=="RESTART_APP":self._attr_device_class=ButtonDeviceClass.RESTART
    @property
    def available(self):return self.controller.app.online
    async def async_press(self):await self.controller.app.command(self.command)

class PanelAction(KnobEntity,ButtonEntity):
    def __init__(self,controller,key,name,command,icon,category=None):
        super().__init__(controller,key);self._attr_name=name;self.command=command
        self._attr_icon=icon;self._attr_entity_category=category
        if command=="REBOOT":self._attr_device_class=ButtonDeviceClass.RESTART

    @property
    def available(self):return self.controller.device.available

    async def async_press(self):
        if self.command=="SCREENSHOT":await self.controller.device.refresh()
        else:await self.controller.device.command(self.command)
