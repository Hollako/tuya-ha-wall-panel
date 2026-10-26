"""Device automation triggers owned by Tuya Wall Panel, using HA's event helper."""
import probatio as vol
from homeassistant.components.device_automation import DEVICE_TRIGGER_BASE_SCHEMA,InvalidDeviceAutomationConfig
from homeassistant.components.homeassistant.triggers import event as event_trigger
from homeassistant.helpers import device_registry as dr
from .const import DOMAIN
from .hardware import EVENTS

TRIGGER_SCHEMA=DEVICE_TRIGGER_BASE_SCHEMA.extend({vol.Required("type"):str,vol.Required("subtype"):str})
TYPE={"press":"button_short_press","double_press":"button_double_press","long_press":"button_long_press","rotate_left":"rotate_left","rotate_right":"rotate_right"}
TRIGGERS={(TYPE[event],key):(key,event) for key,events in EVENTS.items() for event in events}

def panel_entry(hass,device_id):
    device=dr.async_get(hass).async_get(device_id,include_composite_devices=False)
    entry=hass.config_entries.async_get_entry(device.config_entry_id) if device else None
    if not entry or entry.domain!=DOMAIN:raise InvalidDeviceAutomationConfig("Not a Tuya Wall Panel device")
    return entry

async def async_get_triggers(hass,device_id):
    panel_entry(hass,device_id)
    return [{"platform":"device","domain":DOMAIN,"device_id":device_id,"type":kind,"subtype":key} for kind,key in TRIGGERS]

async def async_validate_trigger_config(hass,config):
    config=TRIGGER_SCHEMA(config)
    panel_entry(hass,config["device_id"])
    if (config["type"],config["subtype"]) not in TRIGGERS:raise InvalidDeviceAutomationConfig("Unsupported panel gesture")
    return config

async def async_attach_trigger(hass,config,action,trigger_info):
    await async_validate_trigger_config(hass,config)
    key,event=TRIGGERS[(config["type"],config["subtype"])]
    event_config=event_trigger.TRIGGER_SCHEMA({"platform":"event","event_type":DOMAIN+"_event","event_data":{"device_id":config["device_id"],"control":key,"event_type":event}})
    return await event_trigger.async_attach_trigger(hass,event_config,action,trigger_info,platform_type="device")
