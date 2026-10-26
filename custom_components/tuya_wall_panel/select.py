"""User can select the active target from HA as well as on the physical knob."""
from homeassistant.components.select import SelectEntity
from .entity import KnobEntity
from .hardware import HardwareEntity,SPECS

async def async_setup_entry(hass,entry,async_add_entities):
    c=entry.runtime_data
    async_add_entities([ActiveTarget(c),*(HardwareSelect(c,s) for s in SPECS if s.domain=="select")])

class HardwareSelect(HardwareEntity,SelectEntity):
    def __init__(self,controller,spec):
        super().__init__(controller,spec);self._attr_options=list(spec.options)
    @property
    def current_option(self):return self.hardware_value
    async def async_select_option(self,option):await self.send(option)

class ActiveTarget(KnobEntity,SelectEntity):
    _attr_name="Knob active entity"
    _attr_icon="mdi:format-list-bulleted"
    def __init__(self,controller):super().__init__(controller,"active_target")

    @property
    def options(self):return self.controller.selection_labels()

    @property
    def current_option(self):
        options=self.options
        return options[self.controller.menu.selected] if options else None

    async def async_select_option(self,option):
        options=self.options
        if option in options:await self.controller.select(self.controller.targets[options.index(option)])
