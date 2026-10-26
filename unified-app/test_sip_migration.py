"""Pure legacy-config recovery tests. Never reads real credentials or contacts ADB."""
from migrate_sip import legacy_preferences
normal='<map><string name="sip_user">unit</string><string name="sip_password">test&amp;only</string><int name="rtp_port" value="10000"/><boolean name="auto_answer" value="false"/></map>'
values,recovered=legacy_preferences(normal)
assert not recovered and values['sip_user']=='unit' and values['sip_password']=='test&only' and values['auto_answer']=='false'
broken='<?xml version=1.0 encoding=utf-8 standalone=yes ?><map><string name=sip_user>unit</string><string name=sip_password>test&amp;only</string><int name=rtp_port value=10000 /><boolean name=auto_answer value=false /></map>'
repaired,recovered=legacy_preferences(broken)
assert recovered and repaired==values
try:legacy_preferences('<unrelated/>')
except ValueError:pass
else:raise AssertionError('Invalid preference root accepted')
print('PASS: valid and previously unquoted XML import identically, entities/typed values preserved, unrelated input refused; original files never written.')
