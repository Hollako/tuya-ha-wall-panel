"""Confirmed MQTT membership drives HA directly, without polling or optimism."""
import copy,json,types,unittest
from unittest.mock import AsyncMock,patch
import test_groups as g

class GroupPushTests(unittest.IsolatedAsyncioTestCase):
    asyncSetUp=g.GroupTests.asyncSetUp
    asyncTearDown=g.GroupTests.asyncTearDown
    def message(self,retained=False):
        return types.SimpleNamespace(retain=retained,payload=json.dumps({'v':1,'self':g.A,'panels':[
            {'id':g.A,'active':True,'leader':g.A,'connected':True},
            {'id':g.B,'active':False,'leader':'','connected':False}]}))

    async def test_confirmed_leave_updates_both_group_toggles_without_http(self):
        main=g.GroupTests.primary(self);remote=g.players.RoomPlayer(self.c,g.B)
        self.assertIn('media_player.kitchen_room_audio',main.group_members)
        self.g._membership(self.message())
        self.assertEqual(main.group_members,['media_player.existing_audio'])
        self.assertEqual(remote.group_members,[])
        self.assertFalse(remote.record['active']);self.assertEqual(self.session.calls,[])
        self.assertEqual(remote.volume_level,.2) # Other state is preserved.

    async def test_join_confirmation_is_also_reflected_directly(self):
        self.g._membership(self.message())
        message=self.message();value=json.loads(message.payload)
        value['panels'][1].update(active=True,leader=g.A,connected=True)
        message.payload=json.dumps(value);self.g._membership(message)
        self.assertEqual(set(self.g.members(g.A)),{g.A,g.B})

    async def test_retained_disabled_unknown_and_invalid_pushes_are_ignored(self):
        original=copy.deepcopy(self.g.data)
        self.g._membership(self.message(retained=True));self.assertEqual(self.g.data,original)
        self.c.options['group_enabled']=False;self.g._membership(self.message());self.assertEqual(self.g.data,original)
        self.c.options['group_enabled']=True
        for mutate in (
            lambda x:x.update(self=g.B),
            lambda x:x['panels'][1].update(id=g.C),
            lambda x:x['panels'][1].update(active='false'),
            lambda x:x['panels'][1].update(active=True,leader=''),
            lambda x:x['panels'].append(x['panels'][1]),
        ):
            value=json.loads(self.message().payload);mutate(value)
            self.g._membership(types.SimpleNamespace(retain=False,payload=json.dumps(value)))
            self.assertEqual(self.g.data,original)

    async def test_inflight_http_response_cannot_undo_newer_push(self):
        old=copy.deepcopy(self.g.data)
        async def response(*args):
            self.g._membership(self.message());return old
        with patch.object(g.groups,'directory',side_effect=response):await self.g.refresh()
        self.assertFalse(self.g.panels[g.B]['active']);self.assertTrue(self.g.online)

    async def test_ha_requests_fresh_membership_after_subscribing(self):
        g.t.mqtt.async_publish.reset_mock();g.t.mqtt.async_subscribe.reset_mock()
        await self.g.start()
        self.assertIn(self.c.base+'/app/group_state',[call.args[1] for call in g.t.mqtt.async_subscribe.await_args_list])
        g.t.mqtt.async_publish.assert_awaited_once_with(self.hass,self.c.base+'/app/group_state/get','',qos=1,retain=False)

    async def test_command_rejection_does_not_change_group_membership(self):
        self.session.reply={'accepted':False,'errors':{g.B:'offline'}}
        with self.assertRaises(g.t.controller.HomeAssistantError):await self.g.command('leave',target=g.B)
        self.assertTrue(self.g.panels[g.B]['active']);self.assertEqual(self.g._push_version,0)

if __name__=='__main__':unittest.main()
