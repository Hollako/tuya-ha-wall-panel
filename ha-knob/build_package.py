"""Package only this integration, never credentials or other HA configuration."""
import json,zipfile,shutil
from pathlib import Path
root=Path(__file__).resolve().parent
component=root.parent/'custom_components'/'tuya_wall_panel'
manifest=json.loads((component/'manifest.json').read_text(encoding='utf-8'))
if manifest['domain']!=component.name:
    raise ValueError('Component folder and manifest domain must match')
labels={"name":"Wall Panel name","mqtt_base":"Panel MQTT base topic",
        "entities":"Entities to control or display (in selected order)",
        "brightness_step":"Light brightness per click (%)","cover_step":"Cover position per click (%)",
        "volume_step":"Media volume per click (%)",
        "temperature_step":"Temperature per click (0 uses the entity's step)","menu_timeout":"Selection menu timeout (seconds)",
        "auto_led":"Automatically show white for available / red for unavailable",
        "include_clock":"Include Clock page","clock_format":"Clock format"}
descriptions={"entities":"Choose any number of Climate, Weather, Light, Cover and Media Player entities. Media pages rotate volume; press plays/pauses where supported, otherwise toggles mute where available.",
    "include_clock":"Adds a read-only time/date page using Home Assistant's time zone. Clock appears after the chosen entities.",
    "temperature_step":"Set 0 to use the thermostat's own temperature step."}
strings={"title":"Tuya Wall Panel",
    "config":{"step":{"user":{"title":"Set up your Wall Panel","description":"Double press: next entity. Hold: entity selection menu. In the menu, rotate to highlight, press to select, hold to cancel. Buttons, relays, audio and displays belong to this integration; MQTT is the local transport.","data":labels,"data_description":descriptions}},
              "error":{"invalid_topic":"Enter a valid MQTT base topic without wildcards.","invalid_entities":"Choose only Climate, Weather, Light, Cover or Media Player entities."},
              "abort":{"already_configured":"This panel already has a controller. Use Configure to change its entity list."}},
    "options":{"step":{"init":{"title":"Configure your panel","menu_options":{"entities":"Entities, Weather and Clock","buttons":"Button assignments and scenes","order":"Page order"}},
        "entities":{"title":"Choose knob entities and behavior","description":"Add or remove entities here. They are not fixed in code. Use Page order to arrange the selection menu.","data":{k:v for k,v in labels.items() if k not in ('name','mqtt_base')},"data_description":descriptions},
        "buttons":{"title":"Choose a physical button","data":{"button":"Button (top to bottom)"}},
        "button":{"title":"Button {button} assignments","description":"Assign single and double press independently. For scenes or other HA actions, select Run Home Assistant actions and use the action editor below. Local relay assignments are saved on the panel and work without HA/Wi-Fi; configuring one does not switch the relay. All four buttons work on the first press with the main display off and do not wake it. Single press waits 400 ms to distinguish double press. These buttons do not provide a reliable long-press signal.","data":{"press":"Single press function","press_actions":"Single press HA actions (used only in Run HA actions mode)","double_press":"Double press function","double_press_actions":"Double press HA actions (used only in Run HA actions mode)"}},
        "order":{"title":"Arrange the knob menu","description":"Current order:\n\n{order}\n\nChoose a page, move it up or down, and submit. Repeat as needed, then choose Save order and submit.","data":{"page":"Page","direction":"Action"}}},
        "abort":{"no_pages":"Enable Clock or choose entities before arranging pages."},
               "error":{"invalid_entities":"Choose only Climate, Weather, Light, Cover or Media Player entities.","invalid_actions":"Choose at least one valid HA action for each Run HA actions assignment."}}}
strings['device_automation']={'trigger_type':{'button_short_press':'Single press','button_double_press':'Double press','button_long_press':'Long press','rotate_left':'Turn counterclockwise','rotate_right':'Turn clockwise'},'trigger_subtype':{'knob':'Knob',**{'button_'+str(i):'Button '+str(i) for i in range(1,5)}}}
strings['options']['step']['init']['menu_options']['dashboard']='Dashboard URL'
strings['options']['step']['dashboard']={'title':'Dashboard URL','description':'Save the dashboard URL for the Wall Panel app. The app hardware service must be running for delivery. Dashboard delivery is currently under review. The Dashboard URL entity shows panel confirmation.','data':{'dashboard_url':'Dashboard URL (HTTP/HTTPS, up to 255 characters)'}}
strings['options']['error']['invalid_dashboard_url']='Enter an HTTP/HTTPS URL up to 255 characters, without spaces or embedded credentials. Leave blank to clear.'
(component/'translations').mkdir(exist_ok=True)
for path in [component/'strings.json',component/'translations'/'en.json']:
    path.write_text(json.dumps(strings,indent=2)+'\n',encoding='utf-8')
with zipfile.ZipFile(root/'tuya-wall-panel.zip','w',zipfile.ZIP_DEFLATED) as out:
    for path in sorted(component.rglob('*')):
        if path.is_file() and '__pycache__' not in path.parts:out.write(path,path.relative_to(component.parent.parent))
print('Built',root/'tuya-wall-panel.zip')
exploded=root/'tuya-wall-panel'
if not exploded.resolve().is_relative_to(root.resolve()) or exploded.is_symlink() or exploded.is_junction():
    raise RuntimeError('Expanded package directory must stay inside this project')
for parent in (exploded/'custom_components',exploded/'custom_components/tuya_wall_panel'):
    if parent.is_symlink() or parent.is_junction():raise RuntimeError('Expanded package must not redirect to another directory')
shutil.copytree(component,exploded/'custom_components/tuya_wall_panel',dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pyc'))
print('Updated expanded component folder for copying.')
