"""Constants for the configurable panel controller."""
DOMAIN = "tuya_wall_panel"
from homeassistant.const import Platform
DEFAULT_BASE = "wallpanel/px30_172_16_1_26"
DOMAINS = ("climate", "light", "cover", "media_player")
PLATFORMS = (Platform.SELECT, Platform.SWITCH, Platform.BUTTON, Platform.SENSOR, Platform.MEDIA_PLAYER, Platform.LIGHT, Platform.IMAGE, Platform.EVENT, Platform.TEXT, Platform.NUMBER)
DEFAULTS = {"entities": [], "brightness_step": 5, "cover_step": 5, "volume_step": 5,
            "temperature_step": 0, "menu_timeout": 20, "auto_led": False,
            "include_clock": False, "screensaver_enabled": True, "screensaver_weather": "", "clock_format": "24h", "buttons": {}, "page_order": [], "dashboard_url": ""}
BUTTON_MODES = [
    {"value":"none","label":"No additional action"},
    {"value":"next","label":"Knob: next entity"},
    {"value":"previous","label":"Knob: previous entity"},
    {"value":"menu","label":"Knob: open / cancel selection menu"},
    {"value":"confirm","label":"Knob: select highlighted entity"},
    {"value":"relay1","label":"Toggle relay 1 locally (works offline)"},
    {"value":"relay2","label":"Toggle relay 2 locally (works offline)"},
    {"value":"action","label":"Run Home Assistant actions / scene"},
]
