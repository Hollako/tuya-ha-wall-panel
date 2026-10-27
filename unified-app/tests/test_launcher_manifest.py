"""Verify Android launcher registration and separate administrator entry."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=ET.parse(Path(__file__).resolve().parents[1]/'AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
activities={x.get(a+'name'):x for x in root.find('application').findall('activity')}
home=activities['.HomeActivity'];settings=activities['.MainActivity']
filters=home.findall('intent-filter');assert len(filters)==1
assert {x.get(a+'name') for x in filters[0].findall('action')}=={'android.intent.action.MAIN'}
assert {x.get(a+'name') for x in filters[0].findall('category')}=={'android.intent.category.HOME','android.intent.category.DEFAULT'}
assert home.get(a+'exported')=='true' and home.get(a+'launchMode')=='singleTask'
assert home.get(a+'taskAffinity')=='local.wallpanel.home'
assert any(x.get(a+'name')=='android.intent.category.LAUNCHER' for f in settings.findall('intent-filter') for x in f.findall('category'))
assert activities['.DashboardActivity'].get(a+'exported')=='false'
assert activities['.PanelSetupActivity'].get(a+'exported')=='false'
assert activities['.SipSettingsActivity'].get(a+'exported')=='false'
assert activities['.IntercomCallActivity'].get(a+'exported')=='false'
assert activities['.IntercomCallActivity'].get(a+'taskAffinity')==home.get(a+'taskAffinity')
services={x.get(a+'name'):x for x in root.find('application').findall('service')}
assert services['.SipService'].get(a+'exported')=='false'
assert services['.ExternalAppService'].get(a+'exported')=='false'
receivers={x.get(a+'name'):x for x in root.find('application').findall('receiver')};assert set(receivers)=={'.BootReceiver','.AppRestartReceiver'}
assert receivers['.AppRestartReceiver'].get(a+'exported')=='false' and not receivers['.AppRestartReceiver'].findall('intent-filter')
receiver=receivers['.BootReceiver'];assert receiver.get(a+'name')=='.BootReceiver' and receiver.get(a+'exported')=='false'
assert {x.get(a+'name') for f in receiver.findall('intent-filter') for x in f.findall('action')}=={'android.intent.action.BOOT_COMPLETED','android.intent.action.MY_PACKAGE_REPLACED'}
assert 'android.permission.RECEIVE_BOOT_COMPLETED' in {x.get(a+'name') for x in root.findall('uses-permission')}
print('PASS: Home/Default registration, private setup/preview, protected boot/update receiver and preserved administrator opt-out.')
