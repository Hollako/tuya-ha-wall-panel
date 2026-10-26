"""Management boundaries, dark-display behavior and image push semantics."""
import importlib,json,types,unittest
from unittest.mock import patch
import test_controller as t
class Entity:pass
class Image(Entity):
    def __init__(self,hass):self.hass=hass
t.module('homeassistant.helpers.entity',Entity=Entity,DeviceInfo=lambda **kw:kw)
t.module('homeassistant.components.light',LightEntity=Entity,ColorMode=types.SimpleNamespace(BRIGHTNESS='brightness'),ATTR_BRIGHTNESS='brightness')
t.module('homeassistant.components.image',ImageEntity=Image)
t.module('homeassistant.components.button',ButtonEntity=Entity,ButtonDeviceClass=types.SimpleNamespace(RESTART='restart'))
t.sys.modules['homeassistant.const'].EntityCategory=types.SimpleNamespace(CONFIG='config',DIAGNOSTIC='diagnostic')
light=importlib.import_module('tuya_wall_panel.light');image=importlib.import_module('tuya_wall_panel.image');buttons=importlib.import_module('tuya_wall_panel.button')
PNG=b'\x89PNG\r\n\x1a\n'+b'0'*20+b'\x00\x00\x00\x00IEND\xaeB`\x82'

class DeviceTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        t.mqtt.async_publish.reset_mock();self.hass=t.FakeHass()
        self.c=t.controller.KnobController(self.hass,types.SimpleNamespace(entry_id='device',title='Tuya Wall Panel',data={'mqtt_base':'test/panel'},options={}))
        self.c.online=True
        self.c.device._state(types.SimpleNamespace(payload=json.dumps({'protocol_version':1,'display_on':False,'brightness':120})))
    async def asyncTearDown(self):await self.c.stop()

    async def test_display_state_reflects_darkness_without_losing_brightness(self):
        panel=light.MainDisplay(self.c)
        self.assertFalse(panel.is_on);self.assertEqual(panel.brightness,120)
        await panel.async_turn_off()
        self.assertEqual(t.mqtt.async_publish.await_args.args[2],'DISPLAY_OFF')
    async def test_brightness_before_wake_and_zero_means_off(self):
        panel=light.MainDisplay(self.c)
        await panel.async_turn_on(brightness=70)
        self.assertEqual([a.args[2] for a in t.mqtt.async_publish.await_args_list],['70','DISPLAY_ON'])
        self.assertTrue(all(a.kwargs['retain'] is False for a in t.mqtt.async_publish.await_args_list))
        await panel.async_turn_on(brightness=0)
        self.assertEqual(t.mqtt.async_publish.await_args.args[2],'DISPLAY_OFF')
    async def test_offline_invalid_commands_and_bounds_are_rejected(self):
        for level in (0,256,True,2.5):
            with self.assertRaises(t.controller.HomeAssistantError):await self.c.device.brightness(level)
        with self.assertRaises(t.controller.HomeAssistantError):await self.c.device.command('SHUTDOWN')
        self.c.online=False
        with self.assertRaises(t.controller.HomeAssistantError):await self.c.device.command('REBOOT')
        t.mqtt.async_publish.assert_not_awaited()
    async def test_reboot_is_explicit_nonretained_and_not_sent_by_online_setup(self):
        await self.c._online_setup()
        self.assertFalse(any(a.args[2]=='REBOOT' for a in t.mqtt.async_publish.await_args_list))
        t.mqtt.async_publish.reset_mock()
        reboot=buttons.PanelAction(self.c,'reboot','Reboot','REBOOT','mdi:restart')
        await reboot.async_press()
        self.assertEqual(t.mqtt.async_publish.await_args.args[2],'REBOOT')
        self.assertFalse(t.mqtt.async_publish.await_args.kwargs['retain'])
    async def test_image_rejects_retained_invalid_and_truncated_payloads(self):
        for payload,retain in ((PNG,True),(b'not an image',False),(PNG[:-1],False)):
            self.c.device._image(types.SimpleNamespace(payload=payload,retain=retain))
            self.assertIsNone(self.c.device.image)
        self.c.device._image(types.SimpleNamespace(payload=PNG,retain=False))
        screen=image.PanelScreenshot(self.c);timestamp=screen.image_last_updated
        self.assertEqual(await screen.async_image(),PNG)
        self.assertEqual(await screen.async_image(),PNG)
        self.assertEqual(screen.image_last_updated,timestamp)
        self.assertFalse(screen.extra_state_attributes['display_on'])
        self.assertEqual(screen.extra_state_attributes['refresh_interval_seconds'],300)
    async def test_refresh_is_throttled_and_never_wakes_the_display(self):
        await self.c.device.refresh();await self.c.device.refresh()
        t.mqtt.async_publish.assert_awaited_once()
        self.assertEqual(t.mqtt.async_publish.await_args.args[2],'SCREENSHOT')

    async def test_app_owner_schedules_screenshots_but_manual_refresh_remains(self):
        import asyncio
        self.c.app.online=True
        self.c.device._state(types.SimpleNamespace(payload=json.dumps({'protocol_version':1,'owner':'app','management_available':True,'display_on':False,'brightness':120})))
        self.c.device._requested=0;self.c.device.tick();await asyncio.sleep(0)
        t.mqtt.async_publish.assert_not_awaited()
        await self.c.device.refresh();t.mqtt.async_publish.assert_awaited_once()
        self.c.app.online=False;self.assertFalse(self.c.device.available)
        with self.assertRaises(t.controller.HomeAssistantError):await self.c.device.command('REBOOT')
        self.c.device._state(types.SimpleNamespace(payload=json.dumps({'protocol_version':1,'owner':'legacy','display_on':False,'brightness':120})))
        self.assertTrue(self.c.device.available)

    async def test_automatic_refresh_waits_five_minutes(self):
        import asyncio
        self.c.device._requested=1000
        with patch('tuya_wall_panel.device.time',types.SimpleNamespace(monotonic=lambda:1299)):
            self.c.device.tick();await asyncio.sleep(0)
            t.mqtt.async_publish.assert_not_awaited()
        with patch('tuya_wall_panel.device.time',types.SimpleNamespace(monotonic=lambda:1300)):
            self.c.device.tick();await asyncio.sleep(0)
            t.mqtt.async_publish.assert_awaited_once()

if __name__=='__main__':unittest.main(verbosity=2)
