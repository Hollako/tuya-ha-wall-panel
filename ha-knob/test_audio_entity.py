"""HA audio entity behavior with fake MQTT, without operating the panel."""
import enum,importlib,json,types,unittest
from unittest.mock import AsyncMock,patch
import test_controller as t
class Feature(enum.IntFlag):
    TURN_ON=1;TURN_OFF=2;PLAY=4;PAUSE=8;STOP=16;VOLUME_SET=32;VOLUME_STEP=64;VOLUME_MUTE=128
    SELECT_SOURCE=256;PLAY_MEDIA=512;BROWSE_MEDIA=1024;NEXT_TRACK=2048;PREVIOUS_TRACK=4096;SEEK=8192;GROUPING=524288
class Player:
    @property
    def source_list(self):return self._attr_source_list
class Browse:
    def __init__(self,**kwargs):self.__dict__.update(kwargs)
t.module('homeassistant.components.media_player',MediaPlayerEntity=Player,MediaPlayerEntityFeature=Feature,
    MediaPlayerState=enum.StrEnum('MediaPlayerState',{k.upper():k for k in ('off','idle','playing','paused','buffering')}),
    MediaPlayerDeviceClass=types.SimpleNamespace(SPEAKER='speaker'),async_process_play_media_url=lambda h,u:u,
    BrowseMedia=Browse,MediaClass=types.SimpleNamespace(DIRECTORY='directory',TRACK='track'),MediaType=types.SimpleNamespace(MUSIC='music'))
source=t.module('homeassistant.components.media_source',is_media_source_id=lambda v:v.startswith('media-source://'),
    async_resolve_media=AsyncMock(return_value=types.SimpleNamespace(url='http://ha.local/signed.wav')),async_browse_media=AsyncMock())
t.module('homeassistant.helpers.entity',Entity=type('Entity',(),{}),DeviceInfo=lambda **kw:kw)
media=importlib.import_module('tuya_wall_panel.media_player')
class AudioTests(unittest.IsolatedAsyncioTestCase):
    async def test_artwork_uses_mqtt_cache_without_receiver_http(self):
        import base64,hashlib
        picture=b'\xff\xd8test-thumbnail\xff\xd9';identity=hashlib.sha256(picture).hexdigest()
        self.report(.4,source='AirPlay',artwork_id=identity)
        self.assertIsNone(self.player.media_image_hash)
        payload={'id':identity,'content_type':'image/jpeg','image':base64.b64encode(picture).decode()}
        self.c.audio._artwork(types.SimpleNamespace(payload=json.dumps(payload)))
        self.assertEqual(self.player.media_image_hash,identity)
        self.assertEqual(await self.player.async_get_media_image(),(picture,'image/jpeg'))
        for invalid in ({**payload,'id':'b'*64},{**payload,'image':'not-base64!'},{**payload,'content_type':'text/html'}):
            self.c.audio._artwork(types.SimpleNamespace(payload=json.dumps(invalid)))
            self.assertEqual(self.player.media_image_hash,identity)
        self.report(.5,source='AirPlay',artwork_id='c'*64)
        self.assertIsNone(self.player.media_image_hash);self.assertEqual(await self.player.async_get_media_image(),(None,None))
        self.report(.5,source='Android playback',artwork_id=identity)
        self.assertIsNone(self.player.media_image_hash)
        self.c.audio._artwork(types.SimpleNamespace(payload='{"id":"","image":""}'))
        self.assertIsNone(self.c.audio.artwork)
    async def asyncSetUp(self):
        self.hass=t.FakeHass();t.mqtt.async_publish.reset_mock()
        entry=types.SimpleNamespace(entry_id='audio',title='Tuya Wall Panel',data={'mqtt_base':'test/panel'},options={})
        self.c=t.controller.KnobController(self.hass,entry);self.c.online=True;self.c.audio.online=True
        self.player=media.PanelPlayer(self.c);self.player.hass=self.hass;self.player.entity_id='media_player.test_audio'
    async def asyncTearDown(self):await self.c.stop()
    async def test_sources_and_availability(self):
        self.assertEqual(self.player.source_list,['Local Playback','Line Input']);self.assertTrue(self.player.available)
        self.c.audio.online=False;self.assertFalse(self.player.available)
    async def test_source_command_is_real_and_nonretained(self):
        await self.player.async_select_source('Line Input')
        call=t.mqtt.async_publish.await_args
        self.assertEqual(json.loads(call.args[2]),{'command':'source','value':'Line Input'});self.assertFalse(call.kwargs['retain'])
    async def test_unknown_source_rejected(self):
        with self.assertRaises(t.controller.HomeAssistantError):await self.player.async_select_source('Independent speaker output')
        t.mqtt.async_publish.assert_not_awaited()
    async def test_local_playback_name_preserves_panel_command_and_automations(self):
        self.report(.5,sources=['Android playback','Line Input','AirPlay'],volume_scope='Android playback')
        self.assertEqual(self.player.source,'Local Playback')
        self.assertEqual(self.player.source_list,['Local Playback','Line Input','AirPlay'])
        self.assertEqual(self.player.extra_state_attributes['volume_scope'],'Local Playback')
        for name in ('Local Playback','Android playback'):
            await self.player.async_select_source(name)
            self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'source','value':'Android playback'})
        self.report(.6,source='Local Playback',sources=['Local Playback','Line Input'])
        self.assertEqual(self.player.source,'Local Playback')
        self.report(.9,sources=['Android playback','Local Playback'])
        self.assertEqual(self.player.volume_level,.6)
    async def test_shared_receiver_source_and_metadata(self):
        self.report(.5,source='AirPlay',sources=['Android playback','Line Input','AirPlay'],title='Example',artist='Artist',album='Album')
        self.assertIn('AirPlay',self.player.source_list)
        self.assertEqual(self.player.source,'AirPlay');self.assertEqual(self.player.media_title,'Example')
        self.assertEqual(self.player.media_artist,'Artist');self.assertEqual(self.player.media_album_name,'Album')
        await self.player.async_select_source('AirPlay')
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2])['value'],'AirPlay')
    async def test_malformed_source_list_cannot_replace_valid_state(self):
        self.report(.4)
        for invalid in ('AirPlay',['AirPlay','AirPlay'],['Unknown'],[{}]):
            self.report(.9,sources=invalid)
            self.assertEqual(self.player.volume_level,.4)
    async def test_live_receiver_calibration_commands(self):
        await self.c.audio.command('receiver_delay',value=-125)
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'receiver_delay','value':-125})
        self.assertFalse(t.mqtt.async_publish.await_args.kwargs['retain'])
        for command,value in [('receiver_delay',501),('receiver_delay',.5),('receiver_trim',101),('receiver_trim',True),('receiver_enabled',1)]:
            with self.assertRaises(t.controller.HomeAssistantError):await self.c.audio.command(command,value=value)
    async def test_unsupported_queue_and_announcement_rejected(self):
        for kw in [{'announce':True},{'enqueue':'add'}]:
            with self.assertRaises(t.controller.HomeAssistantError):await self.player.async_play_media('music','http://ha.local/audio.wav',**kw)
        t.mqtt.async_publish.assert_not_awaited()
    async def test_media_source_resolved_before_playback(self):
        await self.player.async_play_media('music','media-source://media_source/local/test.wav',extra={'title':'Test'})
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'play_url','url':'http://ha.local/signed.wav','title':'Test'})
    async def test_skip_commands_and_features(self):
        self.assertFalse(self.player._attr_supported_features&Feature.SEEK)
        self.assertTrue(self.player._attr_supported_features&Feature.NEXT_TRACK)
        self.assertTrue(self.player._attr_supported_features&Feature.PREVIOUS_TRACK)
        await self.player.async_media_next_track()
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'next'})
        await self.player.async_media_previous_track()
        self.assertEqual(json.loads(t.mqtt.async_publish.await_args.args[2]),{'command':'previous'})

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
