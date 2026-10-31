"""MQTT endpoint migration without a broker, panel, or HA instance."""
import asyncio,copy,hashlib,hmac,json,types,unittest
from unittest.mock import AsyncMock,patch
import test_groups as g

groups=g.groups
A=g.A;B=g.B
HOST='172.16.1.40'
TOKEN='test-secret'

class EndpointTests(unittest.IsolatedAsyncioTestCase):
    asyncSetUp=g.GroupTests.asyncSetUp
    asyncTearDown=g.GroupTests.asyncTearDown

def announcement(key=A,host=HOST,port=8099):
    return types.SimpleNamespace(payload=json.dumps({'v':1,'id':key,'host':host,'port':port}),retain=True)

class EndpointBehaviorTests(EndpointTests):
    async def test_retained_address_recovers_old_ip_and_all_calls_use_new_endpoint(self):
        self.g._expected_id=A;self.g.online=False
        with patch.object(groups,'verify_endpoint',new=AsyncMock(return_value=g.snapshot())) as verify:
            self.g._address(announcement());await asyncio.gather(*list(self.c._tasks))
        verify.assert_awaited_once_with(self.hass,HOST,8099,A,'private-token')
        self.assertTrue(self.g.online);self.assertEqual(self.g.connection,(HOST,8099))
        self.assertEqual(self.g._store.value['id'],A)
        self.assertNotIn('private-token',json.dumps(self.g._store.value))
        await self.g.command('room',target=A,volume=.4);await self.g.library(A)
        self.assertTrue(all(call[1].startswith('http://'+HOST+':8099/') for call in self.session.calls))
        self.assertEqual(self.c.options['group_host'],'panel.local') # No config reload cycle.

    async def test_failed_identity_never_replaces_live_endpoint(self):
        self.g._expected_id=A
        with patch.object(groups,'verify_endpoint',new=AsyncMock(side_effect=ValueError('bad identity'))):
            self.g._address(announcement());await asyncio.gather(*list(self.c._tasks))
        self.assertEqual(self.g.connection,('panel.local',8099));self.assertTrue(self.g.online)
        self.assertEqual(self.session.calls,[])

    async def test_other_panel_and_malformed_messages_are_ignored(self):
        self.g._expected_id=A
        with patch.object(groups,'verify_endpoint',new=AsyncMock()) as verify:
            self.g._address(announcement(key=B))
            for host in ('127.0.0.1','0.0.0.0','169.254.1.2','224.0.0.1','255.255.255.255','http://172.16.1.40','172.16.1.40/path',True):
                self.g._address(announcement(host=host))
            self.g._address(announcement(port=True));self.g._address(announcement(port=70000))
            self.g._address(types.SimpleNamespace(payload='x'*1025))
        verify.assert_not_awaited();self.assertFalse(self.c._tasks)

    async def test_disabled_grouping_remains_disabled(self):
        self.c.options['group_enabled']=False;self.g._address(announcement())
        self.assertFalse(self.c._tasks)

    async def test_unchanged_live_announcement_is_noop(self):
        self.g._expected_id=A;self.g._endpoint=(HOST,8099)
        self.g._address(announcement());self.assertFalse(self.c._tasks)

    async def test_saved_endpoint_survives_reload_but_manual_configuration_wins(self):
        saved={'seed':self.g._seed(),'id':A,'host':HOST,'port':8099}
        self.g._store.async_load=AsyncMock(return_value=saved)
        await self.g.start();self.assertEqual(self.g.connection,(HOST,8099))
        await self.g.stop()
        new=groups.PanelGroups(self.c);new._store.async_load=AsyncMock(return_value=saved)
        self.c.options['group_host']='172.16.1.60'
        try:
            await new.start();self.assertEqual(new.connection,('172.16.1.60',8099))
        finally:await new.stop()

    async def test_stale_ip_cannot_silently_change_pinned_identity(self):
        self.g._expected_id=B
        await self.g.refresh()
        self.assertFalse(self.g.online);self.assertEqual(self.g._expected_id,B)

    async def test_proof_is_checked_before_any_token_is_sent(self):
        calls=[]
        class IdentitySession:
            def get(inner,url,**kwargs):
                calls.append((url,kwargs))
                if url.endswith('/identity'):
                    nonce=kwargs['params']['challenge']
                    proof=hmac.new(TOKEN.encode(),f'wallpanel-address-v1\n{A}\n{nonce}'.encode(),hashlib.sha256).hexdigest()
                    return g.Response({'v':1,'id':A,'proof':proof})
                return g.Response(g.snapshot())
        with patch.object(groups,'async_get_clientsession',return_value=IdentitySession()):
            data=await groups.verify_endpoint(self.hass,HOST,8099,A,TOKEN)
        self.assertEqual(data['self'],A);self.assertNotIn('headers',calls[0][1])
        self.assertEqual(calls[1][1]['headers'],{'Authorization':'Bearer '+TOKEN})
        self.assertTrue(all(call[1]['allow_redirects'] is False for call in calls))

    async def test_bad_proof_stops_before_authenticated_request(self):
        session=g.Session({'v':1,'id':A,'proof':'0'*64})
        with patch.object(groups,'async_get_clientsession',return_value=session):
            with self.assertRaises(ValueError):await groups.verify_endpoint(self.hass,HOST,8099,A,TOKEN)
        self.assertEqual(len(session.calls),1);self.assertNotIn('headers',session.calls[0][2])

    async def test_no_token_still_checks_identity(self):
        calls=[]
        class IdentitySession:
            def get(inner,url,**kwargs):
                calls.append(url)
                return g.Response({'v':1,'id':B,'proof':''})
        with patch.object(groups,'async_get_clientsession',return_value=IdentitySession()):
            with self.assertRaises(ValueError):await groups.verify_endpoint(self.hass,HOST,8099,A,'')
        self.assertEqual(len(calls),1)

if __name__=='__main__':unittest.main()
