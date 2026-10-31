"""Group entities/coordinator with fake HTTP; no panels or HA instance operated."""
import copy,importlib,types,unittest,sys
from unittest.mock import AsyncMock,patch,Mock
import test_audio_entity as a
import test_controller as t
groups=importlib.import_module('tuya_wall_panel.groups')
players=importlib.import_module('tuya_wall_panel.group_player')
A='panel-aaaaaaaa';B='panel-bbbbbbbb';C='panel-cccccccc'
def snapshot():
    return {'v':1,'self':A,'panels':[{'id':key,'name':name,'online':True,'authorized':True,'active':True,'leader':A,'volume':volume,'muted':False,'state':'playing'} for key,name,volume in ((A,'Living',.1),(B,'Kitchen',.2))],
        'presets':[{'id':'group-aaaaaaaa','name':'Ground Floor','leader':A,'members':[A,B]}]}

class Response:
    def __init__(self,value):self.value=value
    async def __aenter__(self):return self
    async def __aexit__(self,*args):pass
    async def json(self):return copy.deepcopy(self.value)
    def raise_for_status(self):pass
class Session:
    def __init__(self,value):self.value=value;self.calls=[];self.reply={'accepted':True};self.library_value={'node':A,'folders':[],'tracks':[],'nextOffset':None}
    def get(self,url,**kw):self.calls.append(('get',url,kw));return Response(self.library_value if url.endswith('/library') else self.value)
    def post(self,url,**kw):self.calls.append(('post',url,kw));return Response(self.reply)

class GroupTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.hass=t.FakeHass();entry=types.SimpleNamespace(entry_id='groups',title='Wall Panel',data={'mqtt_base':'test/panel'},options={'group_enabled':True,'group_host':'panel.local','group_port':8099,'group_token':'private-token'})
        self.c=t.controller.KnobController(self.hass,entry);self.g=self.c.groups;self.g.data=snapshot();self.g.online=True;self.g.players={A:'media_player.living_room_audio',B:'media_player.kitchen_room_audio'}
        self.session=Session(snapshot());self.patcher=patch.object(groups,'async_get_clientsession',return_value=self.session);self.patcher.start()
    async def asyncTearDown(self):self.patcher.stop();await self.c.stop()
    async def test_room_volume_and_mute_are_targeted_and_not_optimistic(self):
        room=players.RoomPlayer(self.c,B);await room.async_set_volume_level(.025);await room.async_mute_volume(True)
        commands=[call[2]['json'] for call in self.session.calls if call[0]=='post']
        self.assertEqual(commands,[{'op':'room','target':B,'volume':.03},{'op':'room','target':B,'muted':True}])
        self.assertEqual(room.volume_level,.2);self.assertEqual(self.g.panels[A]['volume'],.1)
        self.assertEqual(self.session.calls[0][2]['headers']['Authorization'],'Bearer private-token')
    async def test_join_adds_rooms_to_current_source_and_unjoin_is_per_room(self):
        self.g.players[C]='media_player.bedroom';room=players.RoomPlayer(self.c,B);await room.async_join_players(['media_player.bedroom'])
        body=self.session.calls[0][2]['json'];self.assertEqual(body['leader'],A);self.assertEqual(set(body['members']),{A,B,C})
        await room.async_unjoin_player();body=[c for c in self.session.calls if c[0]=='post'][-1][2]['json'];self.assertEqual(body,{'op':'leave','target':B})
        with self.assertRaises(t.controller.HomeAssistantError):await room.async_join_players(['media_player.unrelated_sonos'])
    async def test_saved_group_play_pause_and_volume_use_group_api(self):
        player=players.SavedGroupPlayer(self.c,'group-aaaaaaaa');self.assertTrue(player.active);self.assertAlmostEqual(player.volume_level,.15)
        await player.async_media_pause();await player.async_set_volume_level(.3)
        bodies=[c[2]['json'] for c in self.session.calls if c[0]=='post'];self.assertEqual(bodies,[{'op':'transport','leader':A,'command':'pause'},{'op':'group_volume','leader':A,'volume':.3}])
        self.g.data['panels'][1]['active']=False;self.assertFalse(player.active)
        with self.assertRaises(t.controller.HomeAssistantError):await player.async_media_pause()

    async def test_bluetooth_input_makes_selected_room_leader_and_preserves_members(self):
        self.g.data['panels'][1]['bluetoothGroupAvailable']=True
        room=players.RoomPlayer(self.c,B)
        self.assertIn('Bluetooth',room.source_list)
        self.assertEqual(room.source,'Group stream')
        await room.async_select_source('Bluetooth')
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'group_input','leader':B,'input':'bluetooth','members':[A,B]})
        self.assertEqual(self.g.panels[B]['volume'],.2)

    async def test_app_input_makes_selected_room_leader_and_preserves_members(self):
        self.g.data['panels'][1]['appAudioGroupAvailable']=True
        player=players.RoomPlayer(self.c,B)
        self.assertIn('App audio',player.source_list)
        await player.async_select_source('App audio')
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'group_input','leader':B,'input':'apps','members':[A,B]})

    async def test_saved_group_input_uses_its_fixed_leader_and_members(self):
        player=players.SavedGroupPlayer(self.c,'group-aaaaaaaa')
        self.assertNotIn('Bluetooth',player.source_list)
        await player.async_select_source('Local music')
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'group_input','leader':A,'input':'file','members':[A,B]})
    async def test_saved_group_activates_without_existing_playback(self):
        for p in self.g.data['panels']:p['active']=False
        await players.SavedGroupPlayer(self.c,'group-aaaaaaaa').async_media_play()
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'activate','id':'group-aaaaaaaa'})
    async def test_new_rooms_and_presets_are_added_once_without_reload(self):
        added=[];self.g.watch('test',added.extend,lambda kind,key:(kind,key));self.assertEqual(len(added),3)
        await self.g.refresh();self.assertEqual(len(added),3)
        self.session.value['panels'].append({'id':C,'name':'Bedroom','online':False});await self.g.refresh();self.assertEqual(added[-1],('room',C))
    async def test_partial_failure_is_reported_and_timing_url_rejects_path_injection(self):
        self.session.reply={'accepted':False,'errors':{B:'Unavailable'}}
        with self.assertRaisesRegex(t.controller.HomeAssistantError,'Kitchen'):await self.g.command('set_group',leader=A,members=[A,B])
        for host in ('http://panel','panel/path','user@panel','x..local'):
            with self.assertRaises(ValueError):groups.group_url(host,8099)
    async def test_offline_hub_only_changes_availability_not_panel_configuration(self):
        room=players.RoomPlayer(self.c,A);self.assertTrue(room.available)
        with patch.object(groups,'directory',side_effect=ValueError('offline')):await self.g.refresh()
        self.assertFalse(room.available);self.assertTrue(self.g.data['panels'][0]['active']);self.assertEqual(self.session.calls,[])

    async def test_ha_browser_lists_panel_folders_and_playable_track_ids(self):
        from tuya_wall_panel.group_library import folder_id
        self.session.library_value.update(folders=[{'id':'folder-'+'a'*32,'name':'Music/Remix','count':17}],tracks=[])
        room=players.RoomPlayer(self.c,A);room.hass=self.hass
        root=await room.async_browse_media();self.assertEqual(root.children[0].title,'Living local music');self.assertEqual(self.session.calls,[])
        folder=await room.async_browse_media(media_content_id=folder_id(A,'root'));self.assertIn('Music/Remix',folder.children[1].title)
        self.session.library_value.update(folders=[],tracks=[{'id':'audio-42','title':'My song','artist':'Artist'}],nextOffset=100)
        songs=await room.async_browse_media(media_content_id=folder_id(A,'all'));self.assertTrue(songs.children[0].can_play);self.assertEqual(songs.children[0].media_content_id,'wallpanel-local://'+A+'/audio-42');self.assertIn('offset=100',songs.children[-1].media_content_id)
        request=self.session.calls[-1];self.assertEqual(request[2]['params']['target'],A)

    async def test_local_track_on_receiver_becomes_source_and_preserves_rooms(self):
        room=players.RoomPlayer(self.c,B);room.hass=self.hass
        await room.async_play_media('wallpanel_local','wallpanel-local://'+B+'/audio-42')
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'play_track','leader':B,'members':[A,B],'track':'audio-42'})

    async def test_saved_group_browses_and_plays_its_source_panel_music(self):
        player=players.SavedGroupPlayer(self.c,'group-aaaaaaaa');player.hass=self.hass
        await player.async_play_media('wallpanel_local','wallpanel-local://'+A+'/audio-4')
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'play_track','leader':A,'members':[A,B],'track':'audio-4'})
        with self.assertRaises(t.controller.HomeAssistantError):await player.async_play_media('wallpanel_local','wallpanel-local://'+B+'/audio-4')

    async def test_library_permission_errors_and_unsafe_media_ids_do_not_start_audio(self):
        from tuya_wall_panel.group_library import track_id,folder_id
        for uri in ('file:///sdcard/song.mp3','wallpanel-local://'+A+'/../../secret','wallpanel-local://'+A+'/audio-1?path=secret'):
            with self.assertRaises(t.controller.HomeAssistantError):track_id(uri)
        self.session.library_value['permissionRequired']=True;room=players.RoomPlayer(self.c,A);room.hass=self.hass
        with self.assertRaisesRegex(t.controller.HomeAssistantError,'music access'):await room.async_browse_media(media_content_id=folder_id(A,'all'))
        self.assertFalse(any(c[0]=='post' for c in self.session.calls))

    def primary(self):
        player=a.media.PanelPlayer(self.c);player.hass=self.hass;player.entity_id='media_player.existing_audio'
        self.c.online=True;self.c.audio.online=True
        self.c.audio.data={'state':'paused','source':'Local Playback','volume':.9,'muted':True,'title':'Old audio','artist':'Old artist'}
        player._register_room()
        return player

    async def test_one_player_for_local_panel_preserves_original_unique_id(self):
        registry=types.SimpleNamespace(async_get_entity_id=Mock(return_value='media_player.old_room'),async_get=Mock(return_value=types.SimpleNamespace(config_entry_id='groups')),async_remove=Mock())
        er=types.ModuleType('homeassistant.helpers.entity_registry');er.async_get=lambda h:registry
        er.async_entries_for_config_entry=lambda r,key:[types.SimpleNamespace(domain='media_player',platform='tuya_wall_panel',unique_id='groups_group_preset_group-aaaaaaaa_player',entity_id='media_player.saved_group'),types.SimpleNamespace(domain='media_player',platform='other',unique_id='groups_group_preset_group-aaaaaaaa_player',entity_id='media_player.unrelated')]
        added=[];self.c.entry.runtime_data=self.c
        helpers=sys.modules['homeassistant.helpers']
        with patch.dict(sys.modules,{'homeassistant.helpers.entity_registry':er}),patch.object(helpers,'entity_registry',er,create=True):
            await a.media.async_setup_entry(self.hass,self.c.entry,added.extend)
            self.assertEqual(len(added),2) # original player and remote room only
            self.assertEqual(added[0]._attr_unique_id,'groups_audio_player')
            self.assertEqual([e.key for e in added if isinstance(e,players.RoomPlayer)],[B])
            self.assertEqual([c.args[0] for c in registry.async_remove.call_args_list],['media_player.saved_group','media_player.old_room'])
            await self.g.refresh();self.assertEqual(len(added),2)

    async def test_saved_groups_are_selected_inside_the_primary_player(self):
        player=self.primary()
        self.assertIn('Group: Ground Floor',player.source_list)
        await player.async_select_source('Group: Ground Floor')
        self.assertEqual(self.session.calls[0][2]['json'],{'op':'activate','id':'group-aaaaaaaa'})
        self.assertEqual(self.g.presets['group-aaaaaaaa']['members'],[A,B])
        self.g.online=False;self.assertNotIn('Group: Ground Floor',player.source_list)

    async def test_same_name_saved_groups_remain_distinguishable(self):
        self.g.data['presets'].append({'id':'group-bbbbbbbb','name':'Ground Floor','leader':B,'members':[B]})
        self.session.value=copy.deepcopy(self.g.data)
        player=self.primary()
        self.assertEqual(len(player.saved_groups),2)
        for label,key in player.saved_groups.items():
            self.assertIn(key,label)
            await player.async_select_source(label)
            self.assertEqual([c for c in self.session.calls if c[0]=='post'][-1][2]['json'],{'op':'activate','id':key})

    async def test_primary_groups_volume_and_library_use_same_player(self):
        player=self.primary()
        self.assertEqual(player.group_members,['media_player.existing_audio','media_player.kitchen_room_audio'])
        self.assertEqual(player.state,'playing');self.assertEqual(player.volume_level,.1)
        self.assertFalse(player.is_volume_muted);self.assertIsNone(player.media_artist)
        await player.async_set_volume_level(.126);await player.async_mute_volume(True)
        await player.async_play_media('wallpanel_local','wallpanel-local://'+A+'/audio-42')
        bodies=[c[2]['json'] for c in self.session.calls if c[0]=='post']
        self.assertEqual(bodies[0],{'op':'room','target':A,'volume':.13})
        self.assertEqual(bodies[1],{'op':'room','target':A,'muted':True})
        self.assertEqual(bodies[2],{'op':'play_track','leader':A,'members':[A,B],'track':'audio-42'})
        root=await player.async_browse_media();self.assertIn('local music',root.children[0].title)

    async def test_primary_and_remote_players_can_join_each_other(self):
        player=self.primary()
        await player.async_join_players(['media_player.kitchen_room_audio'])
        await players.RoomPlayer(self.c,B).async_join_players(['media_player.existing_audio'])
        bodies=[c[2]['json'] for c in self.session.calls if c[0]=='post']
        self.assertTrue(all(b['leader']==A and set(b['members'])=={A,B} for b in bodies))
        player._unregister_room();self.assertNotIn(A,self.g.players);self.assertIn(B,self.g.players)

    async def test_primary_mqtt_survives_group_connection_failure(self):
        player=self.primary();self.g.online=False
        self.assertTrue(player.available);self.assertEqual(player.state,'paused');self.assertEqual(player.volume_level,.9)
        await player.async_media_play()
        self.assertIn('"command":"play"',t.mqtt.async_publish.await_args.args[2])
        with self.assertRaisesRegex(t.controller.HomeAssistantError,'panel address'):await player.async_join_players(['media_player.kitchen_room_audio'])
        self.c.audio.online=False;self.assertFalse(player.available)

    async def test_inactive_bluetooth_uses_smart_play_and_source_controls(self):
        for p in self.g.data['panels']:
            p.update(active=False,nowPlaying={'kind':'bluetooth','state':'playing','title':'Phone song'})
        self.session.value=copy.deepcopy(self.g.data)
        player=self.primary()
        self.assertEqual(player.source,'Bluetooth');self.assertEqual(player.state,'playing');self.assertEqual(player.media_title,'Phone song')
        await player.async_media_play();await player.async_media_next_track()
        await player.async_join_players(['media_player.kitchen_room_audio'])
        bodies=[c[2]['json'] for c in self.session.calls if c[0]=='post']
        self.assertEqual(bodies[0],{'op':'smart_play','leader':A,'members':[A],'command':'play'})
        self.assertEqual(bodies[1],{'op':'media_transport','leader':A,'kind':'bluetooth','command':'next'})
        self.assertEqual(bodies[2],{'op':'smart_play','leader':A,'members':[A,B]})

    async def test_error_diagnosis_distinguishes_address_and_authentication(self):
        with patch.object(groups,'directory',side_effect=TimeoutError()):await self.g.refresh()
        self.assertIn('current IP address',self.g.error);self.assertIn('panel.local:8099',self.g.error)
        error=groups.aiohttp.ClientResponseError(401)
        with patch.object(groups,'directory',side_effect=error):await self.g.refresh()
        self.assertIn('token was rejected',self.g.error)

    async def test_group_start_is_idempotent(self):
        with patch.object(groups,'async_track_time_interval',return_value=Mock()) as timer:
            await self.g.start();await self.g.start();self.assertEqual(timer.call_count,1)

    async def test_device_visit_url_tracks_verified_connection(self):
        self.c.device_id='main-device'
        device=types.SimpleNamespace(id='main-device',configuration_url=None)
        def update(key,**values):
            self.assertEqual(key,device.id)
            device.configuration_url=values['configuration_url']
        registry=types.SimpleNamespace(async_get=Mock(return_value=device),async_update_device=Mock(side_effect=update))
        dr=types.ModuleType('homeassistant.helpers.device_registry');dr.async_get=lambda h:registry
        with patch.dict(sys.modules,{'homeassistant.helpers.device_registry':dr}),patch.object(sys.modules['homeassistant.helpers'],'device_registry',dr,create=True):
            await self.g.refresh();self.assertEqual(device.configuration_url,'http://panel.local:8099/')
            await self.g.refresh();self.assertEqual(registry.async_update_device.call_count,1)
            self.g._endpoint=('172.16.1.40',8099)
            await self.g.refresh();self.assertEqual(device.configuration_url,'http://172.16.1.40:8099/')
            with patch.object(groups,'directory',side_effect=TimeoutError()):await self.g.refresh()
            self.assertEqual(device.configuration_url,'http://172.16.1.40:8099/')

if __name__=='__main__':unittest.main()
