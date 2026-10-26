import json,types,unittest
import test_device as d
import test_unified as t

class DisplayTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.c=t.t.controller.KnobController(t.t.FakeHass(),types.SimpleNamespace(entry_id='display',title='Wall Panel',data={'mqtt_base':'wallpanel/test'},options={}))
        self.c.online=True;self.c.app.online=True
        self.frame={'protocol_version':1,'available':True,'owner':'app','display_on':True,'brightness':180,'actual_brightness':26,'phase':'dim','dim_after':60,'off_after':300,'dim_brightness':26}
        self.state(self.frame);t.t.mqtt.async_publish.reset_mock()
    def state(self,data):self.c.device._display_state(types.SimpleNamespace(payload=json.dumps(data)))
    async def asyncTearDown(self):await self.c.stop()
    async def test_normal_brightness_stable_when_dim_and_legacy_updates_arrive(self):
        self.c.device._state(types.SimpleNamespace(payload=json.dumps({'protocol_version':1,'display_on':True,'brightness':26,'screenshot_sequence':3})))
        light=d.light.MainDisplay(self.c);self.assertEqual(light.brightness,180);self.assertEqual(light.extra_state_attributes['actual_brightness'],26)
        self.assertEqual(self.c.device.data['screenshot_sequence'],3)
    async def test_number_entities_and_minutes_percent_conversion(self):
        dim=t.number.DisplaySetting(self.c,'dim_after','Dim after');off=t.number.DisplaySetting(self.c,'off_after','Display off after');level=t.number.DisplaySetting(self.c,'dim_brightness','Dim brightness')
        self.assertEqual((dim.native_value,off.native_value,level.native_value),(1,5,10));self.assertTrue(dim.available)
        await dim.async_set_native_value(2);self.assertEqual(t.t.mqtt.async_publish.await_args.args[2],'120')
        await level.async_set_native_value(20);self.assertEqual(t.t.mqtt.async_publish.await_args.args[2],'51')
        self.assertFalse(t.t.mqtt.async_publish.await_args.kwargs['retain'])
    async def test_timer_disable_order_and_invalid_values(self):
        await self.c.device.policy('off_after',0);self.assertEqual(t.t.mqtt.async_publish.await_args.args[2],'0')
        for key,value in (('dim_after',5),('off_after',1),('dim_brightness',0),('off_after',1441),('off_after',True),('dim_after',1.5)):
            with self.assertRaises(t.t.controller.HomeAssistantError):await self.c.device.policy(key,value)
    async def test_offline_app_and_legacy_fallback(self):
        self.c.app._state(types.SimpleNamespace(payload='offline'));self.assertFalse(self.c.device.display_available)
        self.state(self.frame);self.assertFalse(self.c.device.display_available) # Retained app state cannot pretend online.
        self.state({**self.frame,'owner':'legacy'});self.assertTrue(self.c.device.display_available)
    async def test_invalid_snapshot_does_not_replace_current_settings(self):
        self.state({**self.frame,'dim_after':True});self.assertEqual(self.c.device.display_data['dim_after'],60)
        self.state({**self.frame,'brightness':0});self.assertEqual(self.c.device.display_data['brightness'],180)
if __name__=='__main__':unittest.main(verbosity=2)
