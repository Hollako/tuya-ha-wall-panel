"""HA entity pickers: selected targets are user configuration, never code."""
import re
import probatio as vol
from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers import selector
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.script import async_validate_actions_config
from homeassistant.exceptions import HomeAssistantError
from .const import DEFAULT_BASE, DEFAULTS, DOMAIN, DOMAINS, BUTTON_MODES
from .dashboard import validate_dashboard_url

def settings_schema(values, initial=False):
    fields = {}
    if initial:
        fields[vol.Required("name", default="Tuya Wall Panel")] = selector.TextSelector()
        fields[vol.Required("mqtt_base", default=DEFAULT_BASE)] = selector.TextSelector()
    fields[vol.Required("entities", default=values.get("entities", []))] = selector.EntitySelector(selector.EntitySelectorConfig(domain=list(DOMAINS), multiple=True,reorder=True))
    for name, minimum, maximum, step in [("brightness_step",1,25,1),("cover_step",1,25,1),("volume_step",1,25,1),("temperature_step",0,5,0.5),("menu_timeout",5,120,1)]:
        fields[vol.Required(name, default=values.get(name,DEFAULTS[name]))] = selector.NumberSelector(selector.NumberSelectorConfig(min=minimum,max=maximum,step=step,mode=selector.NumberSelectorMode.BOX))
    fields[vol.Required("auto_led", default=values.get("auto_led",False))] = selector.BooleanSelector()
    fields[vol.Required("include_clock", default=values.get("include_clock",True))] = selector.BooleanSelector()
    fields[vol.Required("clock_format", default=values.get("clock_format","24h"))] = selector.SelectSelector(selector.SelectSelectorConfig(options=["24h","12h"],mode=selector.SelectSelectorMode.DROPDOWN))
    return vol.Schema(fields)

class KnobConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    VERSION = 1

    async def async_step_user(self, user_input=None):
        errors = {}
        if user_input is not None:
            base = user_input["mqtt_base"].strip().strip("/")
            if not re.fullmatch(r"[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)+",base):
                errors["mqtt_base"] = "invalid_topic"
            elif any(e.partition(".")[0] not in DOMAINS for e in user_input["entities"]):
                errors["entities"] = "invalid_entities"
            else:
                await self.async_set_unique_id(base)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(title=user_input["name"],data={**user_input,"mqtt_base":base,"entities":list(dict.fromkeys(user_input["entities"]))})
        return self.async_show_form(step_id="user",data_schema=settings_schema(user_input or DEFAULTS,True),errors=errors)

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        return KnobOptionsFlow()

class KnobOptionsFlow(config_entries.OptionsFlowWithReload):
    async def async_step_init(self,user_input=None):
        return self.async_show_menu(step_id="init",menu_options=["entities","buttons","order","dashboard"])

    async def async_step_dashboard(self,user_input=None):
        errors={}
        if user_input is not None:
            try:value=validate_dashboard_url(user_input["dashboard_url"])
            except ValueError:errors["dashboard_url"]="invalid_dashboard_url"
            else:return self.async_create_entry(title="",data={**self.config_entry.options,"dashboard_url":value,"dashboard_managed":True})
        return self.async_show_form(step_id="dashboard",data_schema=vol.Schema({
            vol.Required("dashboard_url",default=(user_input or self.values()).get("dashboard_url","")):selector.TextSelector()
        }),errors=errors)

    def values(self):
        return {**DEFAULTS,**self.config_entry.data,**self.config_entry.options}

    async def async_step_entities(self,user_input=None):
        errors={}
        if user_input is not None:
            if any(e.partition(".")[0] not in DOMAINS for e in user_input["entities"]):
                errors["entities"]="invalid_entities"
            else:
                user_input["entities"]=list(dict.fromkeys(user_input["entities"]))
                # Other Configure pages retain their settings when targets change.
                changed=user_input["entities"]!=self.values()["entities"]
                return self.async_create_entry(title="",data={**self.config_entry.options,**user_input,**({"page_order":[]} if changed else {})})
        values={**self.values(),**(user_input or {})}
        return self.async_show_form(step_id="entities",data_schema=settings_schema(values),errors=errors)

    async def async_step_buttons(self,user_input=None):
        if user_input is not None:
            self._button=str(user_input["button"])
            return await self.async_step_button()
        return self.async_show_form(step_id="buttons",data_schema=vol.Schema({vol.Required("button",default="1"):selector.SelectSelector(selector.SelectSelectorConfig(options=[{"value":str(i),"label":f"Button {i}"} for i in range(1,5)],mode=selector.SelectSelectorMode.DROPDOWN))}))

    async def async_step_button(self,user_input=None):
        values=self.values();current=values["buttons"].get(self._button,{})
        errors={}
        if user_input is not None:
            modes={m["value"] for m in BUTTON_MODES}
            if all(user_input.get(g,"none") in modes for g in ("press","double_press")):
                try:
                    for event in ("press","double_press"):
                        if user_input[event]=="action":
                            actions=user_input.get(event+"_actions",[])
                            if not actions:raise vol.Invalid("Choose at least one action")
                            await async_validate_actions_config(self.hass,cv.SCRIPT_SCHEMA(actions))
                except (vol.Invalid,HomeAssistantError,ValueError,TypeError):
                    errors["base"]="invalid_actions";current=user_input
                else:
                    buttons={**values["buttons"],self._button:user_input}
                    return self.async_create_entry(title="",data={**self.config_entry.options,"buttons":buttons})
        schema={}
        for gesture in ("press","double_press"):
            schema[vol.Required(gesture,default=current.get(gesture,"none"))]=selector.SelectSelector(selector.SelectSelectorConfig(options=BUTTON_MODES,mode=selector.SelectSelectorMode.DROPDOWN))
            schema[vol.Optional(gesture+"_actions",default=current.get(gesture+"_actions",[]))]=selector.ActionSelector()
        return self.async_show_form(step_id="button",data_schema=vol.Schema(schema),description_placeholders={"button":self._button},errors=errors)

    async def async_step_order(self,user_input=None):
        values=self.values()
        pages=list(values["entities"])+(["clock.local"] if values["include_clock"] else [])
        if not hasattr(self,"_order"):
            self._order=[e for e in values["page_order"] if e in pages]+[e for e in pages if e not in values["page_order"]]
        if not self._order:return self.async_abort(reason="no_pages")
        if user_input is not None:
            if user_input["direction"]=="save":return self.async_create_entry(title="",data={**self.config_entry.options,"page_order":self._order})
            index=self._order.index(user_input["page"])
            target=max(0,min(len(self._order)-1,index+(-1 if user_input["direction"]=="up" else 1)))
            self._order[index],self._order[target]=self._order[target],self._order[index]
        def name(e):
            state=self.hass.states.get(e)
            return "Clock" if e=="clock.local" else state.name if state else e
        return self.async_show_form(step_id="order",data_schema=vol.Schema({
            vol.Required("page",default=(user_input or {}).get("page",self._order[0])):selector.SelectSelector(selector.SelectSelectorConfig(options=[{"value":e,"label":name(e)} for e in self._order],mode=selector.SelectSelectorMode.DROPDOWN)),
            vol.Required("direction",default="save"):selector.SelectSelector(selector.SelectSelectorConfig(options=[{"value":"up","label":"Move up"},{"value":"down","label":"Move down"},{"value":"save","label":"Save order"}],mode=selector.SelectSelectorMode.DROPDOWN))}),description_placeholders={"order":"\n".join(f"{i+1}. {name(e)}" for i,e in enumerate(self._order))})
