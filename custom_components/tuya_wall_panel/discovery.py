"""Retire this panel's former MQTT discovery metadata after native migration."""
from homeassistant.components import mqtt
from .hardware import LEGACY_ENTITIES

def legacy_discovery_topics(base):
    ident=base.rsplit("/",1)[-1]
    records=list(LEGACY_ENTITIES)+[("event","controls"),("switch","relay_3"),("switch","relay_4")]
    triggers=["rotate_left","rotate_right","press","double_press","long_press","button_press"]
    triggers += [f"button_{i}_press" for i in range(1,5)]
    triggers += [f"button_{i}_double_press" for i in range(1,5)]
    records += [("device_automation",key) for key in triggers]
    return tuple(f"homeassistant/{domain}/{ident}/{key}/config" for domain,key in records)

async def async_retire_discovery(hass,base):
    # This is metadata removal, never a retained hardware command. Repeat after
    # reconnect so an old broker persistence snapshot cannot resurrect duplicates.
    for topic in legacy_discovery_topics(base):
        await mqtt.async_publish(hass,topic,"",qos=1,retain=True)
