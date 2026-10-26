"""Isolated HA dashboard bookmark tests. No HA or MQTT connection."""
import types,unittest,json
from unittest.mock import Mock
import test_controller as base
if not hasattr(base.sys.modules['homeassistant.const'],'EntityCategory'):
    base.sys.modules['homeassistant.const'].EntityCategory=types.SimpleNamespace(CONFIG='config',DIAGNOSTIC='diagnostic')
import test_unified as t
from tuya_wall_panel.dashboard import validate_dashboard_url
from tuya_wall_panel.text import DashboardURL

class DashboardTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.hass=t.t.FakeHass();self.entry=types.SimpleNamespace(entry_id='dashboard',title='Wall Panel',data={'mqtt_base':'wallpanel/test'},options={'buttons':{'1':{'press':'relay1'}},'page_order':['clock.local']})
        def update(entry,**kwargs):entry.options=kwargs['options'];return True
        self.hass.config_entries=types.SimpleNamespace(async_update_entry=Mock(side_effect=update))
        self.c=t.t.controller.KnobController(self.hass,self.entry);self.entity=DashboardURL(self.c)
        t.t.mqtt.async_publish.reset_mock()
    async def asyncTearDown(self):await self.c.stop()
    async def test_preserves_link_query_fragment_and_other_settings_offline(self):
        url='http://172.16.1.22:8098/?device=tablet&fit=screen&kiosk&noedit#page'
        self.c.online=False;self.assertTrue(self.entity.available)
        await self.entity.async_set_value(' '+url+' ')
        self.assertEqual(self.entity.native_value,url);self.assertEqual(self.entry.options['dashboard_url'],url)
        self.assertEqual(self.entry.options['buttons'],{'1':{'press':'relay1'}});self.assertEqual(self.entry.options['page_order'],['clock.local'])
        call=t.t.mqtt.async_publish.await_args;self.assertEqual(call.args[1],self.c.base+'/app/dashboard/set')
        self.assertEqual(json.loads(call.args[2]),{'v':1,'url':url});self.assertEqual(call.kwargs,{'qos':1,'retain':True})
        self.assertFalse(self.entity.extra_state_attributes['applied_to_panel'])
        restored=t.t.controller.KnobController(self.hass,self.entry);self.assertEqual(restored.options['dashboard_url'],url);await restored.stop()
    async def test_clear_saved_link(self):
        await self.entity.async_set_value('https://ha.example/dashboard');await self.entity.async_set_value('')
        self.assertEqual(self.entity.native_value,'');self.assertEqual(json.loads(t.t.mqtt.async_publish.await_args.args[2]),{'v':1,'url':''})
        self.assertTrue(self.entry.options['dashboard_managed'])
    async def test_invalid_links_do_not_persist_or_publish(self):
        for value in ('javascript:alert(1)','file:///test','http://user:pass@host/','http://host:70000/','http://host:0/','http://host/a b','http://host/\nvalue','http://host/'+'x'*255,None):
            with self.subTest(value=value):
                with self.assertRaises(t.t.controller.HomeAssistantError):await self.entity.async_set_value(value)
        self.hass.config_entries.async_update_entry.assert_not_called();t.t.mqtt.async_publish.assert_not_awaited()
    async def test_stable_identity_and_shared_device(self):
        self.assertEqual(self.entity._attr_unique_id,'dashboard_dashboard_url')
        self.assertEqual(self.entity._attr_device_info['identifiers'],{('tuya_wall_panel','wallpanel/test')})
        self.assertEqual(validate_dashboard_url('https://host/?a=1&b=2#fragment'),'https://host/?a=1&b=2#fragment')
    async def test_confirmation_matches_actual_panel_and_online_state(self):
        await self.entity.async_set_value('https://ha.example/new');self.c.online=True;self.c.app.online=True
        self.c.app._dashboard_state(types.SimpleNamespace(payload='{"v":1,"url":"https://ha.example/old"}'))
        self.assertFalse(self.entity.extra_state_attributes['applied_to_panel'])
        self.c.app._dashboard_state(types.SimpleNamespace(payload='{"v":1,"url":"https://ha.example/new"}'))
        self.assertTrue(self.entity.extra_state_attributes['applied_to_panel']);self.c.app.online=False
        self.assertFalse(self.entity.extra_state_attributes['applied_to_panel'])
    async def test_only_managed_or_nonempty_links_replayed_at_start(self):
        await self.c.app.start();t.t.mqtt.async_publish.assert_not_awaited();await self.c.app.stop()
        await self.entity.async_set_value('https://ha.example/');t.t.mqtt.async_publish.reset_mock()
        await self.c.app.start();self.assertEqual(json.loads(t.t.mqtt.async_publish.await_args.args[2])['url'],'https://ha.example/')
if __name__=='__main__':unittest.main(verbosity=2)
