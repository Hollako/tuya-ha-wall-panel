"""No live HA: app setup command and per-module availability boundaries."""
import types,unittest
import test_device
import test_unified as t
from tuya_wall_panel.button import OpenSetup,AppAction

class AppTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.c=t.t.controller.KnobController(t.t.FakeHass(),types.SimpleNamespace(entry_id='app',title='Wall Panel',data={'mqtt_base':'wallpanel/test'},options={}))
        t.t.mqtt.async_publish.reset_mock()
    async def asyncTearDown(self):await self.c.stop()
    async def test_setup_requires_app_online_and_is_nonretained(self):
        button=OpenSetup(self.c);self.assertFalse(button.available)
        with self.assertRaises(t.t.controller.HomeAssistantError):await button.async_press()
        self.c.online=True;self.c.app._state(types.SimpleNamespace(payload='online'));self.assertTrue(button.available);await button.async_press()
        call=t.t.mqtt.async_publish.await_args;self.assertEqual(call.args[1:],('wallpanel/test/app/command/set','OPEN_SETUP'));self.assertEqual(call.kwargs,{'qos':1,'retain':False})
    async def test_disabled_modules_remain_unavailable_and_cannot_command(self):
        self.c.online=True;self.c.hardware.module_online=True
        self.c.hardware._state(types.SimpleNamespace(topic=self.c.base+'/module/capabilities',payload='{"buttons_enabled":false,"relays_enabled":false}'))
        relay=t.switch.PanelSwitch(self.c,t.hardware.SPECS[0]);event=t.events.PanelEvent(self.c,'button_1')
        self.assertFalse(relay.available);self.assertFalse(event.available)
        with self.assertRaises(t.t.controller.HomeAssistantError):await relay.async_turn_on()
        t.t.mqtt.async_publish.assert_not_awaited()
    async def test_recovery_buttons_are_online_only_and_nonretained(self):
        for key,command in (("reload_dashboard","RELOAD_DASHBOARD"),("restart_app","RESTART_APP")):
            button=AppAction(self.c,key,key,command,"mdi:restart")
            self.assertFalse(button.available)
            with self.assertRaises(t.t.controller.HomeAssistantError):await button.async_press()
            self.c.online=True;self.c.app.online=True
            self.assertTrue(button.available);await button.async_press()
            call=t.t.mqtt.async_publish.await_args
            self.assertEqual(call.args[1:],('wallpanel/test/app/command/set',command))
            self.assertEqual(call.kwargs,{'qos':1,'retain':False})
            self.c.app.online=False
        with self.assertRaises(t.t.controller.HomeAssistantError):await self.c.app.command('REBOOT')
    async def test_legacy_fallback_can_restore_capabilities(self):
        self.c.hardware.buttons_enabled=False;self.c.hardware.relays_enabled=False
        self.c.hardware._state(types.SimpleNamespace(topic=self.c.base+'/module/capabilities',payload='{"buttons_enabled":true,"relays_enabled":true}'))
        self.assertTrue(self.c.hardware.buttons_enabled);self.assertTrue(self.c.hardware.relays_enabled)
        self.c.app._state(types.SimpleNamespace(payload='invalid'));self.assertFalse(self.c.app.online)
    async def test_app_audio_offline_only_affects_app_owner(self):
        self.c.app.online=True;self.c.audio.online=True;self.c.audio.owner='legacy'
        self.c.app._state(types.SimpleNamespace(payload='offline'));self.assertTrue(self.c.audio.online)
        self.c.app.online=True;self.c.audio._owner(types.SimpleNamespace(payload='app'))
        self.c.app._state(types.SimpleNamespace(payload='offline'));self.assertFalse(self.c.audio.online)
        self.assertFalse(self.c.hardware.amplifier_online)
    async def test_amplifier_availability_independent_from_media(self):
        self.c.online=True;self.c.audio.online=False
        self.c.hardware._state(types.SimpleNamespace(topic=self.c.base+'/audio/amplifier/availability',payload='online'))
        self.assertTrue(self.c.hardware.available('amplifier'));self.assertFalse(self.c.audio.online)
        self.c.hardware._state(types.SimpleNamespace(topic=self.c.base+'/audio/amplifier/availability',payload='offline'))
        self.assertFalse(self.c.hardware.available('amplifier'))
    async def test_main_connection_will_invalidates_stale_private_online(self):
        self.c.online=True;self.c.app._state(types.SimpleNamespace(payload='online'));self.assertTrue(self.c.app.online)
        self.c._mqtt_available(types.SimpleNamespace(payload='offline'));self.assertFalse(self.c.app.online)
        self.assertFalse(OpenSetup(self.c).available)
        with self.assertRaises(t.t.controller.HomeAssistantError):await self.c.app.open_setup()
        self.c._mqtt_available(types.SimpleNamespace(payload='garbage'));self.assertFalse(self.c.online)
if __name__=='__main__':unittest.main(verbosity=2)
