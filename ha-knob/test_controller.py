"""Behavior checks with fake HA services/MQTT; never connect to or operate a home."""
import asyncio,importlib,sys,types,unittest,json
from datetime import datetime
from pathlib import Path
from unittest.mock import AsyncMock
from zoneinfo import ZoneInfo

def module(name,**attrs):
    out=types.ModuleType(name);out.__dict__.update(attrs);sys.modules[name]=out;return out
for name in ['homeassistant','homeassistant.components','homeassistant.helpers','homeassistant.util']:
    module(name)
mqtt=module('homeassistant.components.mqtt',async_publish=AsyncMock(),async_subscribe=AsyncMock(return_value=lambda:None))
module('homeassistant.core',callback=lambda f:f,Context=type('Context',(),{}))
module('homeassistant.const',Platform=types.SimpleNamespace(SELECT='select',SWITCH='switch',BUTTON='button',SENSOR='sensor',MEDIA_PLAYER='media_player',LIGHT='light',IMAGE='image',EVENT='event',TEXT='text',NUMBER='number'))
module('homeassistant.helpers.entity',Entity=type('Entity',(),{}),DeviceInfo=lambda **kw:kw)
module('homeassistant.exceptions',HomeAssistantError=type('HomeAssistantError',(Exception,),{}))
module('homeassistant.helpers.event',async_track_state_change_event=lambda *a:lambda:None,async_track_time_interval=lambda *a:lambda:None)
class Store:
    def __init__(self,*args):pass
    async def async_load(self):return None
    async def async_save(self,value):self.value=value
    def async_delay_save(self,*args):pass
module('homeassistant.helpers.storage',Store=Store)
module('homeassistant.helpers.config_validation',SCRIPT_SCHEMA=lambda x:x)
class Script:
    def __init__(self,*args,**kwargs):self.async_run=AsyncMock();self.async_unload=AsyncMock()
module('homeassistant.helpers.script',Script=Script,async_validate_actions_config=AsyncMock(side_effect=lambda h,a:a))
dt=module('homeassistant.util.dt',now=lambda:datetime(2026,10,23,0,5,tzinfo=ZoneInfo('Asia/Riyadh')),
    parse_datetime=lambda s:datetime.fromisoformat(s) if s else None,as_local=lambda d:d.astimezone(ZoneInfo('Asia/Riyadh')))
package=module('tuya_wall_panel');package.__path__=[str(Path(__file__).parent.parent/'custom_components'/'tuya_wall_panel')]
model=importlib.import_module('tuya_wall_panel.model')
controller=importlib.import_module('tuya_wall_panel.controller')

def state(value,**attrs):return types.SimpleNamespace(state=value,attributes=attrs,name=attrs.get('friendly_name','Test entity'))
class FakeHass:
    def __init__(self):
        self.values={
            'light.a':state('on',brightness=128,supported_color_modes=['brightness']),
            'light.b':state('off',brightness=100,supported_color_modes=['rgb']),
            'climate.a':state('cool',temperature=24,current_temperature=27,min_temp=16,max_temp=30,target_temp_step=.5,supported_features=1,hvac_modes=['off','cool']),
            'cover.a':state('open',current_position=30,supported_features=15),
            'weather.a':state('sunny',temperature=28,temperature_unit='°C',supported_features=2),
            'media_player.a':state('playing',volume_level=.4,is_volume_muted=False,supported_features=16397,media_title='Test music')}
        self.states=types.SimpleNamespace(get=self.values.get)
        self.services=types.SimpleNamespace(async_call=AsyncMock(return_value={}))
        self.config=types.SimpleNamespace(units=types.SimpleNamespace(temperature_unit='°C'))
        self.loop=asyncio.get_running_loop()
        self.async_create_task=asyncio.create_task

class ModelTests(unittest.TestCase):
    def test_menu_highlight_does_not_select_until_confirm(self):
        m=model.Menu(['light.a','climate.a','clock.local']);m.toggle();m.rotate(1)
        self.assertEqual(m.active,'light.a');m.confirm();self.assertEqual(m.active,'climate.a')
        m.toggle();m.rotate(1);m.toggle();self.assertEqual(m.active,'climate.a')
    def test_position_unknown_never_guessed(self):
        self.assertIsNone(model.adjust('cover.a','open',{'supported_features':15},1,{}))
    def test_cover_clamped(self):
        c=model.adjust('cover.a','open',{'supported_features':15,'current_position':99},1,{})
        self.assertEqual(c.data['position'],100)
    def test_temperature_bounds_and_range(self):
        attrs={'temperature':30,'min_temp':16,'max_temp':30,'supported_features':1}
        self.assertEqual(model.adjust('climate.a','cool',attrs,5,{}).data['temperature'],30)
        attrs.update(supported_features=2,target_temp_low=27,target_temp_high=30)
        c=model.adjust('climate.a','heat_cool',attrs,2,{})
        self.assertEqual((c.data['target_temp_low'],c.data['target_temp_high']),(27,30))
    def test_unsupported_and_unavailable(self):
        self.assertIsNone(model.adjust('switch.a','on',{},1,{}))
        self.assertIsNone(model.adjust('light.a','unavailable',{'supported_color_modes':['rgb']},1,{}))
        self.assertIsNone(model.adjust('light.a','on',{'supported_color_modes':['onoff']},1,{}))
        self.assertIsNone(model.press('cover.a','open',{'supported_features':4}))
    def test_supported_font_and_lengths(self):
        self.assertEqual(model.display_text('25°C\nnow',16),'25°C now')
        self.assertLessEqual(len(model.display_text('A'*100,16)),16)
    def test_media_volume_steps_bounds_and_capabilities(self):
        attrs={'volume_level':.4,'supported_features':4}
        self.assertEqual(model.adjust('media_player.a','playing',attrs,1,{}).data['volume_level'],.45)
        self.assertEqual(model.adjust('media_player.a','playing',attrs,1,{}, {'volume_level':.45}).data['volume_level'],.5)
        self.assertEqual(model.adjust('media_player.a','playing',attrs,-20,{}).data['volume_level'],0)
        self.assertEqual(model.adjust('media_player.a','playing',attrs,20,{}).data['volume_level'],1)
        self.assertIsNone(model.adjust('media_player.a','playing',{'supported_features':4},1,{}))
        self.assertIsNone(model.adjust('media_player.a','playing',{'volume_level':.4,'supported_features':1024},1,{}))
    def test_media_press_uses_only_supported_commands(self):
        attrs={'supported_features':16397,'is_volume_muted':False}
        self.assertEqual(model.press('media_player.a','playing',attrs).service,'volume_mute')
        self.assertEqual(model.media_hold('media_player.a','paused',attrs).service,'media_play')
        attrs['supported_features']=8
        self.assertEqual(model.press('media_player.a','on',attrs).data['is_volume_muted'],True)
        self.assertIsNone(model.press('media_player.a','idle',{'supported_features':512}))
        self.assertIsNone(model.press('media_player.a','unavailable',attrs))
    def test_media_summary_shows_volume_and_title(self):
        self.assertEqual(model.summary('media_player.a','playing',{'volume_level':.4,'media_title':'Song'}),('40%','Playing / Song'))

class ControllerTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        mqtt.async_publish.reset_mock();self.hass=FakeHass()
        self.entry=types.SimpleNamespace(entry_id='test',title='Test panel',data={'mqtt_base':'test/panel','entities':['light.a','climate.a','cover.a','weather.a']},options={})
        self.c=controller.KnobController(self.hass,self.entry);self.c.online=True
    async def asyncTearDown(self):await self.c.stop()
    async def test_menu_never_actuates_and_confirm_selects(self):
        await self.c.gesture('long_press');await self.c.gesture('rotate_right')
        self.assertEqual(self.c.menu.active,'light.a');self.hass.services.async_call.assert_not_awaited()
        await self.c.gesture('press');self.assertEqual(self.c.menu.active,'climate.a')
        self.hass.services.async_call.assert_not_awaited()
        await self.c.gesture('double_press');self.assertEqual(self.c.menu.active,'cover.a')
    async def test_rapid_temperature_clicks_coalesce_without_losing_steps(self):
        await self.c.select('climate.a')
        for _ in range(4):await self.c.gesture('rotate_right')
        await self.c._flush()
        self.assertEqual(self.hass.services.async_call.await_args.args[2]['temperature'],26)
    async def test_first_detent_dispatches_without_waiting_for_rotation_to_stop(self):
        await self.c.select('climate.a');await self.c.gesture('rotate_right')
        await asyncio.sleep(0)
        self.hass.services.async_call.assert_awaited_once_with('climate','set_temperature',{'entity_id':'climate.a','temperature':24.5},blocking=True)
    async def test_slow_service_keeps_rotary_events_and_coalesces_latest_target(self):
        entered=asyncio.Event();release=asyncio.Event()
        async def slow(*args,**kwargs):
            if not entered.is_set():entered.set();await release.wait()
        self.hass.services.async_call.side_effect=slow
        await self.c.select('climate.a');await self.c.gesture('rotate_right');await entered.wait()
        for _ in range(5):await self.c.gesture('rotate_right')
        await self.c.gesture('rotate_left')
        self.assertEqual(len(self.c._pending),1)
        self.assertEqual(self.c._pending['climate.a'].data['temperature'],26.5)
        release.set();await self.c._flush()
        self.assertEqual(self.hass.services.async_call.await_count,2)
        self.assertEqual(self.hass.services.async_call.await_args.args[2]['temperature'],26.5)
    async def test_media_page_volume_and_press_are_supported(self):
        self.entry.options={'entities':['media_player.a']}
        c=controller.KnobController(self.hass,self.entry);c.online=True
        try:
            await c.gesture('rotate_right');await c._flush()
            self.hass.services.async_call.assert_awaited_with('media_player','volume_set',{'entity_id':'media_player.a','volume_level':.45},blocking=True)
            await c.gesture('press')
            self.hass.services.async_call.assert_awaited_with('media_player','volume_mute',{'entity_id':'media_player.a','is_volume_muted':True},blocking=True)
            await c.gesture('long_press')
            self.hass.services.async_call.assert_awaited_with('media_player','media_pause',{'entity_id':'media_player.a'},blocking=True)
            self.assertIsNone(c.menu.cursor)
            self.assertIn('45%',mqtt.async_publish.await_args.args[2])
        finally:await c.stop()
    async def test_local_media_routing_and_navigation_do_not_duplicate_audio(self):
        self.entry.options={'entities':['media_player.a','light.a']}
        c=controller.KnobController(self.hass,self.entry);c.online=True
        c.hardware.local_media_supported=True
        c._is_local_media=lambda eid:eid=='media_player.a'
        try:
            await c.render()
            routes=[json.loads(a.args[2]) for a in mqtt.async_publish.await_args_list if a.args[1].endswith('/knob/media/set')]
            self.assertTrue(routes[-1]['active']);self.assertEqual(routes[-1]['step'],5)
            self.assertEqual(routes[-1]['entity'],'media_player.a')
            for event in ('rotate_right','press','long_press'):
                c._mqtt_event(types.SimpleNamespace(retain=False,payload=json.dumps({'event_type':event,'local_handled':True})))
            await asyncio.sleep(0)
            self.hass.services.async_call.assert_not_awaited()
            c.options['buttons']={'1':{'press':'menu'}}
            await c.button_event('1','press');self.assertIsNotNone(c.menu.cursor)
            self.assertFalse(json.loads([a.args[2] for a in mqtt.async_publish.await_args_list if a.args[1].endswith('/knob/media/set')][-1])['active'])
            await c.button_event('1','press');await c.gesture('double_press')
            self.assertEqual(c.menu.active,'light.a')
            self.assertFalse(json.loads([a.args[2] for a in mqtt.async_publish.await_args_list if a.args[1].endswith('/knob/media/set')][-1])['active'])
            await c.select('media_player.a');await c.set_enabled(False)
            self.assertFalse(json.loads([a.args[2] for a in mqtt.async_publish.await_args_list if a.args[1].endswith('/knob/media/set')][-1])['active'])
        finally:await c.stop()

    async def test_remote_media_mute_icon_tracks_selected_entity_and_clears_on_navigation(self):
        self.entry.options={'entities':['media_player.a','light.a']}
        c=controller.KnobController(self.hass,self.entry);c.online=True;c.hardware.media_icons_supported=True
        try:
            self.hass.values['media_player.a'].attributes['is_volume_muted']=True
            await c.render()
            self.assertEqual(mqtt.async_publish.await_args.args[1],'test/panel/knob/display/media_frame/set')
            self.assertTrue(mqtt.async_publish.await_args.args[2].endswith('\nON'))
            self.hass.values['media_player.a'].attributes['is_volume_muted']=False
            await c.render();self.assertTrue(mqtt.async_publish.await_args.args[2].endswith('\nOFF'))
            await c.gesture('double_press')
            self.assertEqual(mqtt.async_publish.await_args.args[1],'test/panel/knob/display/frame/set')
            self.assertEqual(len(mqtt.async_publish.await_args.args[2].splitlines()),3)
        finally:await c.stop()

    async def test_local_entity_resolution_uses_own_registry_unique_id(self):
        from unittest.mock import patch,Mock
        registry=Mock();registry.async_get_entity_id.return_value='media_player.renamed_panel'
        er=types.ModuleType('homeassistant.helpers.entity_registry');er.async_get=lambda h:registry
        with patch.dict(sys.modules,{'homeassistant.helpers.entity_registry':er}):
            self.assertTrue(self.c._is_local_media('media_player.renamed_panel'))
            self.assertFalse(self.c._is_local_media('media_player.some_other_panel'))
            registry.async_get_entity_id.assert_called_with('media_player','tuya_wall_panel','test_audio_player')

    async def test_pending_commands_keep_original_targets(self):
        await self.c.gesture('rotate_right');await self.c.gesture('double_press');await self.c.gesture('rotate_left');await self.c._flush()
        calls=self.hass.services.async_call.await_args_list
        self.assertEqual({c.args[2]['entity_id'] for c in calls},{'light.a','climate.a'})
    async def test_screensaver_uses_ha_timezone_weather_and_no_control_pages(self):
        self.assertNotIn('clock.local',self.c.targets)
        self.assertNotIn('weather.a',self.c.targets)
        await self.c.render_screensaver()
        call=mqtt.async_publish.await_args
        self.assertEqual(call.args[1],'test/panel/knob/display/screensaver/set')
        self.assertEqual(call.args[2],'10800\n24h\nsunny\n28°C')
        self.assertFalse(call.kwargs['retain'])
        count=mqtt.async_publish.await_count
        await self.c.render_screensaver()
        self.assertEqual(mqtt.async_publish.await_count,count)

    async def test_explicitly_cleared_weather_does_not_restore_legacy_choice(self):
        self.entry.options={'screensaver_weather':''}
        c=controller.KnobController(self.hass,self.entry);c.online=True
        try:
            await c.render_screensaver()
            self.assertEqual(mqtt.async_publish.await_args.args[2],'10800\n24h\nunavailable\n--')
        finally:await c.stop()

    async def test_frames_do_not_publish_follow_or_power(self):
        await self.c.render()
        self.assertTrue(all(call.args[1].endswith('/display/frame/set') for call in mqtt.async_publish.await_args_list))
        self.assertTrue(all(not call.kwargs['retain'] for call in mqtt.async_publish.await_args_list))
    async def test_disabled_discards_pending_actuation(self):
        await self.c.gesture('rotate_right');await self.c.set_enabled(False);await self.c._flush()
        self.hass.services.async_call.assert_not_awaited()
    async def test_retained_events_do_not_actuate(self):
        self.c._mqtt_event(types.SimpleNamespace(retain=True,payload='{"event_type":"press"}'))
        await asyncio.sleep(0)
        self.hass.services.async_call.assert_not_awaited()
    async def test_local_bindings_are_config_only_and_not_double_executed(self):
        self.c.options['buttons']={'1':{'press':'relay1','double_press':'menu'},'4':{'double_press':'relay2'}}
        await self.c._online_setup()
        bindings=[a.args[2] for a in mqtt.async_publish.await_args_list if '/binding/set' in a.args[1]]
        self.assertEqual(bindings,['1,0','0,0','0,0','0,2'])
        await self.c.button_event('1','press');self.hass.services.async_call.assert_not_awaited()
        await self.c.button_event('1','double_press');self.assertIsNotNone(self.c.menu.cursor)
    async def test_ha_page_order_is_used(self):
        self.entry.options={'page_order':['clock.local','cover.a','light.a']}
        c=controller.KnobController(self.hass,self.entry)
        self.assertEqual(c.targets[:3],['cover.a','light.a','climate.a']);await c.stop()
    async def test_screensaver_unavailable_off_and_selected_weather(self):
        self.c.options['screensaver_enabled']=False
        await self.c.render_screensaver()
        self.assertEqual(mqtt.async_publish.await_args.args[2],'OFF')
        self.c.options['screensaver_enabled']=True
        self.hass.values['weather.a']=state('unavailable',temperature=99)
        await self.c.render_screensaver()
        self.assertEqual(mqtt.async_publish.await_args.args[2],'10800\n24h\nunavailable\n--')
        self.entry.options={'screensaver_weather':'weather.other','clock_format':'12h'}
        c=controller.KnobController(self.hass,self.entry);c.online=True
        try:
            await c.render_screensaver()
            self.assertEqual(mqtt.async_publish.await_args.args[2],'10800\n12h\nunavailable\n--')
            self.hass.services.async_call.assert_not_awaited()
        finally:await c.stop()

    async def test_audio_commands_are_not_retained_and_independent_of_knob(self):
        self.c.enabled=False;self.c.audio.online=True
        await self.c.audio.command('source',value='Line Input')
        call=mqtt.async_publish.await_args
        self.assertEqual(call.args[1],'test/panel/audio/command/set')
        self.assertFalse(call.kwargs['retain'])
        self.assertIn('Line Input',call.args[2])
    async def test_audio_offline_rejects_commands(self):
        with self.assertRaises(controller.HomeAssistantError):await self.c.audio.command('play')
        mqtt.async_publish.assert_not_awaited()
    async def test_audio_invalid_state_does_not_replace_valid_state(self):
        import json
        valid={'state':'idle','source':'Android playback','volume':0.7,'muted':False}
        self.c.audio._state(types.SimpleNamespace(payload=json.dumps(valid)))
        self.assertEqual(self.c.audio.data,valid)
        for patch in [{'volume':float('nan')},{'volume':2},{'muted':'false'},{'state':'garbage'},{'source':'unknown'}]:
            self.c.audio._state(types.SimpleNamespace(payload=json.dumps({**valid,**patch})))
            self.assertEqual(self.c.audio.data,valid)

if __name__=='__main__':unittest.main(verbosity=2)
