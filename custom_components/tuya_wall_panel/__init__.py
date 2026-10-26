"""Configurable HA controller for the existing native MQTT panel bridge."""
from homeassistant.components import mqtt
from homeassistant.exceptions import ConfigEntryNotReady
from .const import PLATFORMS
from .controller import KnobController
from .migration import async_unify_registry,async_cleanup_duplicates
from .discovery import async_retire_discovery

async def async_setup_entry(hass, entry):
    if not await mqtt.async_wait_for_mqtt_client(hass):
        raise ConfigEntryNotReady("MQTT is not ready")
    device_id = await async_unify_registry(hass, entry)
    controller = KnobController(hass, entry)
    controller.device_id = device_id
    entry.runtime_data = controller
    try:
        await controller.start()
        await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
        await async_retire_discovery(hass, controller.base)
        await async_cleanup_duplicates(hass, entry)
    except Exception:
        await controller.stop()
        raise
    return True

async def async_unload_entry(hass, entry):
    if await hass.config_entries.async_unload_platforms(entry, PLATFORMS):
        await entry.runtime_data.stop()
        return True
    return False
