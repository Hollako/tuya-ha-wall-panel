"""Isolated migration and protocol checks; never operate a real panel."""
import importlib,json,types,unittest
from unittest.mock import AsyncMock,Mock,patch
import test_controller as t
discovery=importlib.import_module('tuya_wall_panel.discovery')

t.module('homeassistant.components.switch',SwitchEntity=type('SwitchEntity',(),{}))
t.module('homeassistant.components.select',SelectEntity=type('SelectEntity',(),{}))
t.module('homeassistant.components.text',TextEntity=type('TextEntity',(),{}),TextMode=types.SimpleNamespace(TEXT='text'))
t.module('homeassistant.components.number',NumberEntity=type('NumberEntity',(),{}),NumberMode=types.SimpleNamespace(SLIDER='slider',BOX='box'))
t.module('homeassistant.components.event',EventEntity=type('EventEntity',(),{}))
from homeassistant.const import EntityCategory
hardware=importlib.import_module('tuya_wall_panel.hardware')
switch=importlib.import_module('tuya_wall_panel.switch')
select=importlib.import_module('tuya_wall_panel.select')
text=importlib.import_module('tuya_wall_panel.text')
number=importlib.import_module('tuya_wall_panel.number')
events=importlib.import_module('tuya_wall_panel.event')

class HardwareTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.hass=t.FakeHass();self.hass.bus=types.SimpleNamespace(async_fire=Mock())
        self.entry=types.SimpleNamespace(entry_id='test',data={'mqtt_base':'wallpanel/test_panel'},options={},title='Wall Panel')
        self.c=t.controller.KnobController(self.hass,self.entry);self.entry.runtime_data=self.c
        self.c.online=True;self.c.hardware.module_online=True;self.c.hardware.display_online=True
        t.mqtt.async_publish.reset_mock();t.mqtt.async_subscribe.reset_mock()
    async def asyncTearDown(self):await self.c.stop()
    def report(self,path,payload,retain=True):
        self.c.hardware._state(types.SimpleNamespace(topic=self.c.base+path,payload=payload,retain=retain))
    def event(self,key,event,retain=False):
        self.c.hardware._event(types.SimpleNamespace(topic=self.c.base+hardware.event_path(key),payload=json.dumps({'event_type':event,'sequence':12}),retain=retain))
    async def test_all_sixteen_legacy_entities_have_exact_unique_ids(self):
        self.assertEqual(len(hardware.LEGACY_ENTITIES),16)
        self.assertEqual(len(set(hardware.LEGACY_ENTITIES)),16)
        self.assertEqual(switch.PanelSwitch(self.c,hardware.SPECS[0])._attr_unique_id,'test_panel_relay_1')
        event=events.PanelEvent(self.c,'button_4')
        self.assertEqual(event._attr_unique_id,'test_panel_button_4')
        self.assertEqual(event._attr_device_info['identifiers'],{('tuya_wall_panel','wallpanel/test_panel')})
    async def test_discovery_retirement_is_scoped_and_contains_no_hardware_commands(self):
        await discovery.async_retire_discovery(self.hass,self.c.base)
        calls=t.mqtt.async_publish.await_args_list
        self.assertEqual(len(calls),33)
        self.assertEqual(len({c.args[1] for c in calls}),33)
        for call in calls:
            parts=call.args[1].split('/')
            self.assertEqual((parts[0],parts[2],parts[-1]),('homeassistant','test_panel','config'))
            self.assertEqual(call.args[2],'');self.assertTrue(call.kwargs['retain'])
    async def test_subscribing_only_listens_and_sends_no_commands(self):
        await self.c.hardware.start()
        self.assertEqual(t.mqtt.async_subscribe.await_count,21)
        t.mqtt.async_publish.assert_not_awaited()
    async def test_state_validation_and_availability_are_independent(self):
        self.report('/relay/1/state','ON');self.assertEqual(self.c.hardware.values['relay_1'],'ON')
        self.report('/relay/1/state','bad');self.assertEqual(self.c.hardware.values['relay_1'],'ON')
        self.report('/module/availability','offline')
        self.assertFalse(self.c.hardware.available('module'));self.assertTrue(self.c.hardware.available('display'))
        self.c.online=False;self.assertFalse(self.c.hardware.available('display'))
    async def test_relay_commands_are_nonretained_and_feedback_driven(self):
        relay=switch.PanelSwitch(self.c,hardware.SPECS[0]);self.report('/relay/1/state','OFF')
        await relay.async_turn_on()
        self.assertFalse(relay.is_on)
        self.assertEqual(t.mqtt.async_publish.await_args.args[1:],('wallpanel/test_panel/relay/1/set','ON'))
        self.assertEqual(t.mqtt.async_publish.await_args.kwargs,{'qos':1,'retain':False})
        self.c.hardware.module_online=False
        with self.assertRaises(t.controller.HomeAssistantError):await relay.async_turn_off()
    async def test_text_rejects_overflow_unicode_and_newlines(self):
        spec=next(s for s in hardware.SPECS if s.key=='knob_display_value')
        entity=text.PanelText(self.c,spec)
        for value in ('x'*17,'bad\ntext','\U0001f600'):
            with self.assertRaises(t.controller.HomeAssistantError):await entity.async_set_value(value)
        t.mqtt.async_publish.assert_not_awaited()
        await entity.async_set_value('25°C');await entity.async_set_value('')
        self.assertEqual(t.mqtt.async_publish.await_count,2)
    async def test_led_rejects_unverified_colors_and_off(self):
        spec=next(s for s in hardware.SPECS if s.key=='knob_led_color')
        entity=select.HardwareSelect(self.c,spec)
        for option in ('Off','Blue','RGB'):
            with self.assertRaises(t.controller.HomeAssistantError):await entity.async_select_option(option)
        await entity.async_select_option('White')
        self.assertEqual(t.mqtt.async_publish.await_args.args[1:],('wallpanel/test_panel/knob/led/color/set','White'))
    async def test_knob_activity_mode_uses_its_own_nonretained_topic(self):
        spec=next(s for s in hardware.SPECS if s.key=='knob_activity_mode')
        entity=switch.PanelSwitch(self.c,spec)
        self.report('/knob/display/activity/state','ON');self.assertTrue(entity.is_on)
        await entity.async_turn_off()
        call=t.mqtt.async_publish.await_args
        self.assertEqual(call.args[1:],('wallpanel/test_panel/knob/display/activity/set','OFF'))
        self.assertFalse(call.kwargs['retain'])
    async def test_background_render_does_not_override_local_activity_led(self):
        self.c.options['auto_led']=True
        self.c.hardware.values['knob_activity_mode']='ON'
        await self.c.render()
        self.assertFalse(any(call.args[1].endswith('/knob/led/color/set') for call in t.mqtt.async_publish.await_args_list))
        self.c.hardware.values['knob_activity_mode']='OFF'
        await self.c.render()
        self.assertTrue(any(call.args[1].endswith('/knob/led/color/set') for call in t.mqtt.async_publish.await_args_list))
    async def test_amplifier_rejects_nonfinite_or_fractional_values(self):
        spec=next(s for s in hardware.SPECS if s.domain=='number');entity=number.HardwareLevel(self.c,spec)
        for value in (True,-1,101,20.2,float('nan'),float('inf')):
            with self.assertRaises(t.controller.HomeAssistantError):await entity.async_set_native_value(value)
        await entity.async_set_native_value(40.0)
        self.assertEqual(t.mqtt.async_publish.await_args.args[2],'40')
    async def test_external_speakers_use_existing_audio_command(self):
        self.c.audio.online=True
        entity=switch.ExternalSpeakers(self.c);await entity.async_turn_on()
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'external_speakers','value':True})
    async def test_retained_invalid_and_offline_events_never_fire(self):
        listener=Mock();self.c.hardware.listen('button_1',listener)
        self.event('button_1','press',True);self.event('button_1','long_press')
        self.c.hardware.module_online=False;self.event('button_1','press')
        listener.assert_not_called();self.hass.bus.async_fire.assert_not_called()
    async def test_five_independent_event_streams_preserve_metadata(self):
        listeners={key:Mock() for key in hardware.EVENTS};self.c.device_id='device-test'
        for key,listener in listeners.items():self.c.hardware.listen(key,listener)
        self.event('button_2','double_press')
        listeners['button_2'].assert_called_once_with({'event_type':'double_press','sequence':12})
        for key in ('knob','button_1','button_3','button_4'):listeners[key].assert_not_called()
        self.hass.bus.async_fire.assert_called_once_with('tuya_wall_panel_event',{'device_id':'device-test','control':'button_2','event_type':'double_press'})
        t.mqtt.async_publish.assert_not_awaited()

er=t.module('homeassistant.helpers.entity_registry')
dr=t.module('homeassistant.helpers.device_registry')
sys_entity=importlib.import_module('homeassistant.helpers.entity')
sys_entity.entity_sources=lambda hass:hass.sources
import homeassistant.exceptions as exceptions
exceptions.ConfigEntryNotReady=type('ConfigEntryNotReady',(Exception,),{})
migration=importlib.import_module('tuya_wall_panel.migration')

class Registry:
    def __init__(self,entries):self.entities={e.entity_id:e for e in entries};self.moves=[]
    def async_get(self,entity_id):return self.entities.get(entity_id)
    def async_remove(self,entity_id):del self.entities[entity_id]
    def async_get_entity_id(self,domain,platform,unique_id):
        return next((e.entity_id for e in self.entities.values() if e.domain==domain and e.platform==platform and e.unique_id==unique_id),None)
    def async_update_entity_platform(self,entity_id,platform,**values):
        e=self.entities[entity_id];e.platform=platform;e.config_entry_id=values['new_config_entry_id'];e.device_id=values['new_device_id'];self.moves.append(entity_id)

class MigrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.entry=types.SimpleNamespace(entry_id='controller',data={'mqtt_base':'wallpanel/test'})
        self.hass=types.SimpleNamespace(sources={})
        entries=[types.SimpleNamespace(entity_id=d+'.original_'+k,domain=d,platform='mqtt',unique_id='test_'+k,device_id='mqtt-device',config_entry_id='broker',name='User name',disabled_by='user') for d,k in hardware.LEGACY_ENTITIES]
        entries.append(types.SimpleNamespace(entity_id='light.original_display',domain='light',platform='tuya_wall_panel',unique_id='controller_main_display',device_id='controller-device',config_entry_id='controller'))
        self.registry=Registry(entries)
        self.devices=types.SimpleNamespace(async_update_device=Mock(),async_remove_device=Mock(),async_get_or_create=Mock())
        er.async_get=lambda hass:self.registry
        er.async_entries_for_config_entry=lambda registry,entry_id:[e for e in registry.entities.values() if e.config_entry_id==entry_id]
        er.async_entries_for_device=lambda registry,device_id,**kw:[e for e in registry.entities.values() if e.device_id==device_id]
        dr.async_get=lambda hass:self.devices
    async def test_migrates_in_place_preserving_ids_names_and_disabled_choices(self):
        ids=set(self.registry.entities)
        result=await migration.async_unify_registry(self.hass,self.entry)
        self.assertEqual(result,'controller-device');self.assertEqual(set(self.registry.entities),ids)
        self.assertEqual(len(self.registry.moves),16)
        for key in ids-{'light.original_display'}:
            e=self.registry.entities[key]
            self.assertEqual((e.platform,e.config_entry_id,e.device_id),('tuya_wall_panel','controller','controller-device'))
            self.assertEqual((e.name,e.disabled_by),('User name','user'))
        self.devices.async_remove_device.assert_called_once_with('mqtt-device')
    async def test_live_entities_block_migration_without_any_changes(self):
        self.hass.sources={'switch.original_relay_1':{}}
        with self.assertRaises(exceptions.ConfigEntryNotReady):await migration.async_unify_registry(self.hass,self.entry)
        self.assertFalse(self.registry.moves);self.devices.async_update_device.assert_not_called()
    async def test_repeated_setup_does_not_create_duplicate_entities(self):
        await migration.async_unify_registry(self.hass,self.entry)
        self.registry.moves.clear();self.devices.async_remove_device.reset_mock()
        await migration.async_unify_registry(self.hass,self.entry)
        self.assertFalse(self.registry.moves);self.devices.async_remove_device.assert_not_called()
    async def test_replayed_mqtt_duplicate_never_replaces_native_original_entity(self):
        await migration.async_unify_registry(self.hass,self.entry)
        original=self.registry.entities['switch.original_relay_1']
        duplicate=types.SimpleNamespace(entity_id='switch.original_relay_1_2',domain='switch',platform='mqtt',unique_id='test_relay_1',device_id='duplicate-mqtt-device',config_entry_id='broker')
        self.registry.entities[duplicate.entity_id]=duplicate;self.hass.sources={duplicate.entity_id:{}}
        self.registry.moves.clear()
        result=await migration.async_unify_registry(self.hass,self.entry)
        self.assertEqual(result,'controller-device');self.assertFalse(self.registry.moves)
        self.assertIs(self.registry.entities[original.entity_id],original)
    async def test_removes_only_unloaded_mqtt_copy_with_native_counterpart(self):
        await migration.async_unify_registry(self.hass,self.entry)
        original=self.registry.entities['switch.original_relay_1']
        duplicate=types.SimpleNamespace(entity_id='switch.original_relay_1_2',domain='switch',platform='mqtt',unique_id='test_relay_1',device_id='duplicate-mqtt-device',config_entry_id='broker')
        self.registry.entities[duplicate.entity_id]=duplicate
        self.devices.async_remove_device.reset_mock()
        removed=await migration.async_cleanup_duplicates(self.hass,self.entry)
        self.assertEqual(removed,[duplicate.entity_id]);self.assertIs(self.registry.entities[original.entity_id],original)
        self.devices.async_remove_device.assert_called_once_with('duplicate-mqtt-device')
    async def test_cleanup_never_removes_unmigrated_or_other_panel_entities(self):
        original_ids=set(self.registry.entities)
        removed=await migration.async_cleanup_duplicates(self.hass,self.entry)
        self.assertFalse(removed);self.assertEqual(original_ids,set(self.registry.entities))
    async def test_refuses_to_take_over_unrelated_mqtt_entities(self):
        self.registry.entities['sensor.unrelated']=types.SimpleNamespace(entity_id='sensor.unrelated',domain='sensor',platform='mqtt',unique_id='other_device',device_id='mqtt-device',config_entry_id='broker')
        with self.assertRaises(exceptions.ConfigEntryNotReady):await migration.async_unify_registry(self.hass,self.entry)
        self.assertFalse(self.registry.moves);self.devices.async_update_device.assert_not_called()
    async def test_other_panel_base_is_not_claimed(self):
        self.entry.data={'mqtt_base':'wallpanel/different'}
        await migration.async_unify_registry(self.hass,self.entry)
        self.assertFalse(self.registry.moves);self.devices.async_remove_device.assert_not_called()

if __name__=='__main__':unittest.main(verbosity=2)
