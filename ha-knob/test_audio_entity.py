"""HA audio entity behavior with fake MQTT, without operating the panel."""
import enum,importlib,json,types,unittest
from unittest.mock import AsyncMock,patch
import test_controller as t
class Feature(enum.IntFlag):
    TURN_ON=1;TURN_OFF=2;PLAY=4;PAUSE=8;STOP=16;VOLUME_SET=32;VOLUME_STEP=64;VOLUME_MUTE=128
    SELECT_SOURCE=256;PLAY_MEDIA=512;BROWSE_MEDIA=1024;NEXT_TRACK=2048;PREVIOUS_TRACK=4096;SEEK=8192
class Player:
    @property
    def source_list(self):return self._attr_source_list
t.module('homeassistant.components.media_player',MediaPlayerEntity=Player,MediaPlayerEntityFeature=Feature,
    MediaPlayerState=enum.StrEnum('MediaPlayerState',{k.upper():k for k in ('off','idle','playing','paused','buffering')}),
    MediaPlayerDeviceClass=types.SimpleNamespace(SPEAKER='speaker'),async_process_play_media_url=lambda h,u:u)
source=t.module('homeassistant.components.media_source',is_media_source_id=lambda v:v.startswith('media-source://'),
    async_resolve_media=AsyncMock(return_value=types.SimpleNamespace(url='http://ha.local/signed.wav')),async_browse_media=AsyncMock())
t.module('homeassistant.helpers.entity',Entity=type('Entity',(),{}),DeviceInfo=lambda **kw:kw)
media=importlib.import_module('tuya_wall_panel.media_player')
class AudioTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.hass=t.FakeHass();t.mqtt.async_publish.reset_mock()
        entry=types.SimpleNamespace(entry_id='audio',title='Tuya Wall Panel',data={'mqtt_base':'test/panel'},options={})
        self.c=t.controller.KnobController(self.hass,entry);self.c.online=True;self.c.audio.online=True
        self.player=media.PanelPlayer(self.c);self.player.hass=self.hass;self.player.entity_id='media_player.test_audio'
    async def asyncTearDown(self):await self.c.stop()
    async def test_sources_and_availability(self):
        self.assertEqual(self.player.source_list,['Android playback','Line Input']);self.assertTrue(self.player.available)
        self.c.audio.online=False;self.assertFalse(self.player.available)
    async def test_source_command_is_real_and_nonretained(self):
        await self.player.async_select_source('Line Input')
        call=t.mqtt.async_publish.await_args
        self.assertEqual(json.loads(call.args[2]),{'command':'source','value':'Line Input'});self.assertFalse(call.kwargs['retain'])
    async def test_unknown_source_rejected(self):
        with self.assertRaises(t.controller.HomeAssistantError):await self.player.async_select_source('Independent speaker output')
        t.mqtt.async_publish.assert_not_awaited()
    async def test_unsupported_queue_and_announcement_rejected(self):
        for kw in [{'announce':True},{'enqueue':'add'}]:
            with self.assertRaises(t.controller.HomeAssistantError):await self.player.async_play_media('music','http://ha.local/audio.wav',**kw)
        t.mqtt.async_publish.assert_not_awaited()
    async def test_media_source_resolved_before_playback(self):
        await self.player.async_play_media('music','media-source://media_source/local/test.wav',extra={'title':'Test'})
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'play_url','url':'http://ha.local/signed.wav','title':'Test'})
    async def test_seek_and_skip_not_advertised_without_verified_support(self):
        self.assertFalse(self.player._attr_supported_features&(Feature.SEEK|Feature.NEXT_TRACK|Feature.PREVIOUS_TRACK))

    def report(self,volume,muted=False,**extra):
        self.c.audio._state(types.SimpleNamespace(payload=json.dumps({'state':'idle','source':'Android playback',
            'volume':volume,'muted':muted,'volume_steps':20,'audio_session':'session-a',**extra})))

    async def test_volume_does_not_bounce_to_stale_report(self):
        self.report(.4)
        await self.player.async_set_volume_level(.8)
        self.assertEqual(self.player.volume_level,.8)
        self.report(.4);self.assertEqual(self.player.volume_level,.8)
        self.report(.8);self.assertEqual(self.player.volume_level,.8);self.assertFalse(self.c.audio._pending)

    async def test_latest_slider_target_wins_and_device_steps_are_respected(self):
        self.report(.4)
        await self.player.async_set_volume_level(.8)
        await self.player.async_set_volume_level(.63)
        self.report(.8);self.assertEqual(self.player.volume_level,.65)
        self.report(.65);self.assertFalse(self.c.audio._pending)

    async def test_zero_then_positive_volume_clears_mute_immediately(self):
        self.report(.4)
        await self.player.async_set_volume_level(0)
        self.assertEqual(self.player.volume_level,0);self.assertFalse(self.player.is_volume_muted)
        self.report(0)
        await self.player.async_set_volume_level(.8)
        self.report(0,True)
        self.assertEqual(self.player.volume_level,.8);self.assertFalse(self.player.is_volume_muted)
        self.report(.8);self.assertFalse(self.c.audio._pending)

    async def test_pending_timeout_restores_reported_state(self):
        self.report(.4)
        with patch('tuya_wall_panel.audio.time',types.SimpleNamespace(monotonic=lambda:100)):
            await self.player.async_set_volume_level(.8)
        with patch('tuya_wall_panel.audio.time',types.SimpleNamespace(monotonic=lambda:105)):
            self.c.audio.tick()
        self.assertEqual(self.player.volume_level,.4);self.assertFalse(self.c.audio._pending)

    async def test_publish_failure_rolls_back_and_offline_or_new_session_clears_pending(self):
        self.report(.4)
        t.mqtt.async_publish.side_effect=RuntimeError('offline')
        try:
            with self.assertRaises(RuntimeError):await self.player.async_set_volume_level(.8)
        finally:t.mqtt.async_publish.side_effect=None
        self.assertEqual(self.player.volume_level,.4)
        await self.player.async_set_volume_level(.8)
        self.report(.6,audio_session='session-b')
        self.assertEqual(self.player.volume_level,.6)
        await self.player.async_set_volume_level(.8)
        self.c.audio._availability(types.SimpleNamespace(payload='offline'))
        self.assertFalse(self.c.audio._pending)

    async def test_rejected_command_reports_actual_state_and_invalid_values_are_not_sent(self):
        self.report(.4)
        await self.player.async_set_volume_level(.8)
        request=json.loads(t.mqtt.async_publish.await_args.args[2])['request_id']
        self.report(.4,request_id=request,error='Command rejected')
        self.assertEqual(self.player.volume_level,.4)
        t.mqtt.async_publish.reset_mock()
        for value in (float('nan'),True,-1,2):
            with self.assertRaises(t.controller.HomeAssistantError):await self.player.async_set_volume_level(value)
        t.mqtt.async_publish.assert_not_awaited()

if __name__=='__main__':unittest.main(verbosity=2)
