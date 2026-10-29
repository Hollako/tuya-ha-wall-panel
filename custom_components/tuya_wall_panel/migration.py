"""Move unloaded MQTT entities through HA's supported registry migration API."""
import asyncio
import logging
import re
from homeassistant.exceptions import ConfigEntryNotReady
from homeassistant.helpers import entity_registry as er,device_registry as dr
from homeassistant.helpers.entity import entity_sources
from homeassistant.helpers.storage import Store
from .const import DOMAIN
from .hardware import LEGACY_ENTITIES

_LOGGER=logging.getLogger(__name__)

def _is_audio_room_entity(entity,devices,entry):
    """Exclude only proven room rows, never another controller or unknown device."""
    prefix=entry.entry_id+"_group_room_"
    if entity.platform!=DOMAIN or not entity.unique_id.startswith(prefix):return False
    match=re.fullmatch(r"(panel-[A-Za-z0-9-]{8,80})_(?:player|timing|connection)",entity.unique_id[len(prefix):])
    if not match or not entity.device_id:return False
    device=devices.async_get(entity.device_id)
    return device is not None and (DOMAIN,"audio_room:"+match[1]) in device.identifiers

async def async_cleanup_duplicates(hass,entry):
    """Remove only unloaded MQTT copies with a proven native counterpart.

    Empty discovery messages cannot remove registry-only leftovers when MQTT
    never loaded their old configs. Never remove a live MQTT entity directly.
    """
    registry=er.async_get(hass);devices=dr.async_get(hass)
    ident=entry.data["mqtt_base"].rsplit("/",1)[-1]
    removed=[];old_devices=set()
    for attempt in range(11):
        pending=False
        for domain,key in LEGACY_ENTITIES:
            unique_id=ident+"_"+key
            native_id=registry.async_get_entity_id(domain,DOMAIN,unique_id)
            duplicate_id=registry.async_get_entity_id(domain,"mqtt",unique_id)
            if not native_id or not duplicate_id:continue
            native=registry.async_get(native_id)
            if native.config_entry_id!=entry.entry_id:continue
            if duplicate_id in entity_sources(hass):pending=True;continue
            duplicate=registry.async_get(duplicate_id)
            if duplicate.device_id and duplicate.device_id!=native.device_id:old_devices.add(duplicate.device_id)
            registry.async_remove(duplicate_id);removed.append(duplicate_id)
        if not pending:break
        await asyncio.sleep(.2)
    for device_id in old_devices:
        if not er.async_entries_for_device(registry,device_id,include_disabled_entities=True):
            devices.async_remove_device(device_id)
    if removed:_LOGGER.info("Removed %d unused MQTT copies of migrated Wall Panel entities",len(removed))
    if pending:_LOGGER.warning("Some old Wall Panel MQTT entities are still loaded; leaving their registry entries intact")
    return removed

async def async_unify_registry(hass,entry):
    registry=er.async_get(hass);devices=dr.async_get(hass)
    base=entry.data["mqtt_base"];ident=base.rsplit("/",1)[-1]
    legacy=[]
    for domain,key in LEGACY_ENTITIES:
        # Once the native row exists it owns the original entity ID. A replayed
        # MQTT row is a duplicate, removed by discovery retirement after setup.
        if registry.async_get_entity_id(domain,DOMAIN,ident+"_"+key):continue
        entity_id=registry.async_get_entity_id(domain,"mqtt",ident+"_"+key)
        if entity_id:legacy.append(registry.async_get(entity_id))
    # A hot transfer could leave two active owners or erase the old registry row.
    if any(e.entity_id in entity_sources(hass) for e in legacy):
        raise ConfigEntryNotReady("Stop HA, suspend only this panel's MQTT discovery, then start HA to migrate unloaded entities")
    ours=er.async_entries_for_config_entry(registry,entry.entry_id)
    # One entry owns the primary hardware controller plus discovered audio rooms.
    # Room rows stay on their own devices; legacy MQTT hardware moves only to the
    # primary controller. Preserve the ambiguity guard for genuinely unknown rows.
    device_ids={e.device_id for e in ours if e.device_id and not _is_audio_room_entity(e,devices,entry)}
    if len(device_ids)>1:raise ConfigEntryNotReady("Wall Panel has multiple controller devices; migration requires review")
    legacy_devices={e.device_id for e in legacy if e.device_id}
    known_ids={e.entity_id for e in legacy}
    for device_id in legacy_devices-device_ids:
        if any(e.entity_id not in known_ids for e in er.async_entries_for_device(registry,device_id,include_disabled_entities=True)):
            raise ConfigEntryNotReady("MQTT device contains unrelated entities; refusing to move them")
    audit=Store(hass,1,DOMAIN+".unified_migration."+entry.entry_id)
    previous=await audit.async_load()
    if legacy and not previous:
        await audit.async_save({"entities":[{"entity_id":e.entity_id,"unique_id":e.unique_id,"device_id":e.device_id,"config_entry_id":e.config_entry_id} for e in legacy],
                                "controller_device_ids":sorted(device_ids),"mqtt_device_ids":sorted(legacy_devices)})
    # Keep the existing controller device ID, labels, area and user name.
    if device_ids:
        device_id=next(iter(device_ids))
        devices.async_update_device(device_id,new_identifiers={(DOMAIN,base)},name="Tuya Wall Panel",manufacturer="Sznaner",model="G10 / PX30 Android wall panel")
    else:
        device_id=devices.async_get_or_create(config_entry_id=entry.entry_id,identifiers={(DOMAIN,base)},name="Tuya Wall Panel",manufacturer="Sznaner",model="G10 / PX30 Android wall panel").id
    for entity in legacy:
        registry.async_update_entity_platform(entity.entity_id,DOMAIN,new_config_entry_id=entry.entry_id,new_config_subentry_id=None,new_device_id=device_id)
    # Only empty legacy devices are removed. Existing native entity IDs stay intact.
    for old_id in legacy_devices-{device_id}:
        if not er.async_entries_for_device(registry,old_id,include_disabled_entities=True):
            devices.async_remove_device(old_id)
    return device_id
