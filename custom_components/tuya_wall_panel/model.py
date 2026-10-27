"""Pure menu/control rules, independent of HA and of any particular entity IDs."""
from dataclasses import dataclass
import math
import unicodedata

ALLOWED_DOMAINS = {"climate", "weather", "light", "cover", "media_player"}

def number(value, default=None):
    try:
        out = float(value)
        return out if math.isfinite(out) else default
    except (TypeError, ValueError):
        return default

def display_text(value, limit):
    """Only send characters the panel's installed native atlas can display."""
    value = str(value).replace("\n", " ").replace("\r", " ")
    value = unicodedata.normalize("NFKC", value)
    allowed = lambda c: 32 <= ord(c) <= 126 or 160 <= ord(c) <= 255 or ord(c) in (0x2013, 0x2014, 0x2019, 0x2022, 0x2212)
    return "".join(c for c in value if allowed(c))[:limit]

@dataclass
class Menu:
    entities: list[str]
    selected: int = 0
    cursor: int | None = None

    @property
    def active(self):
        return self.entities[self.selected] if self.entities else None

    def next(self):
        if self.entities:
            self.selected = (self.selected + 1) % len(self.entities)
        self.cursor = None

    def toggle(self):
        self.cursor = self.selected if self.cursor is None and self.entities else None

    def rotate(self, delta):
        if self.entities and self.cursor is not None:
            self.cursor = (self.cursor + delta) % len(self.entities)

    def confirm(self):
        if self.cursor is not None:
            self.selected, self.cursor = self.cursor, None

@dataclass(frozen=True)
class Command:
    domain: str
    service: str
    data: dict

def adjust(entity_id, state, attrs, delta, options, pending=None):
    """Plan a bounded supported adjustment; never infer an unknown position."""
    domain = entity_id.partition(".")[0]
    if domain not in ALLOWED_DOMAINS or state in ("unknown", "unavailable"):
        return None
    pending = pending or {}
    if domain == "media_player":
        # HA MediaPlayerEntityFeature.VOLUME_SET = 4. Never invent a volume
        # for entities that only expose up/down or report an unknown level.
        current = pending.get("volume_level", number(attrs.get("volume_level")))
        step = number(options.get("volume_step"), 5)
        if current is None or not 0 <= current <= 1 or not step or step <= 0 or not int(attrs.get("supported_features", 0)) & 4:
            return None
        value = round(max(0, min(1, current + delta * step / 100)), 4)
        return Command(domain, "volume_set", {"entity_id": entity_id, "volume_level": value})
    if domain == "light":
        modes = attrs.get("supported_color_modes", [])
        if not modes or set(modes) <= {"onoff", "unknown"}:
            return None
        current = pending.get("brightness_pct", number(attrs.get("brightness"), 0) * 100 / 255 if state == "on" else 0)
        value = max(0, min(100, current + delta * options.get("brightness_step", 5)))
        return Command(domain, "turn_off", {"entity_id": entity_id}) if value == 0 else Command(domain, "turn_on", {"entity_id": entity_id, "brightness_pct": round(value)})
    if domain == "cover":
        current = pending.get("position", number(attrs.get("current_position")))
        if current is None or not int(attrs.get("supported_features", 0)) & 4:
            return None
        value = round(max(0, min(100, current + delta * options.get("cover_step", 5))))
        return Command(domain, "set_cover_position", {"entity_id": entity_id, "position": value})
    if domain == "climate":
        step = number(options.get("temperature_step"), 0) or number(attrs.get("target_temp_step"), 0.5)
        minimum, maximum = number(attrs.get("min_temp"), 7), number(attrs.get("max_temp"), 35)
        if not step or step <= 0 or minimum > maximum:
            return None
        if state == "heat_cool":
            low = pending.get("target_temp_low", number(attrs.get("target_temp_low")))
            high = pending.get("target_temp_high", number(attrs.get("target_temp_high")))
            if low is None or high is None or low > high or not int(attrs.get("supported_features", 0)) & 2:
                return None
            shift = max(minimum - low, min(maximum - high, delta * step))
            return Command(domain, "set_temperature", {"entity_id": entity_id, "target_temp_low": round(low + shift, 2), "target_temp_high": round(high + shift, 2)})
        current = pending.get("temperature", number(attrs.get("temperature")))
        if current is None or not int(attrs.get("supported_features", 0)) & 1:
            return None
        value = max(minimum, min(maximum, current + delta * step))
        return Command(domain, "set_temperature", {"entity_id": entity_id, "temperature": round(value, 2)})
    return None

def press(entity_id, state, attrs, previous_mode=None):
    domain = entity_id.partition(".")[0]
    if domain not in ALLOWED_DOMAINS or state in ("unknown", "unavailable"):
        return None
    if domain == "media_player":
        features = int(attrs.get("supported_features", 0))
        if features & 8 and isinstance(attrs.get("is_volume_muted"), bool):
            return Command(domain, "volume_mute", {"entity_id": entity_id, "is_volume_muted": not attrs["is_volume_muted"]})
        return None
    if domain == "light":
        return Command(domain, "turn_off" if state == "on" else "turn_on", {"entity_id": entity_id})
    if domain == "cover" and int(attrs.get("supported_features", 0)) & 8:
        return Command(domain, "stop_cover", {"entity_id": entity_id})
    if domain == "climate":
        modes = attrs.get("hvac_modes", [])
        mode = "off"
        if state == "off":
            choices = [m for m in modes if m != "off"]
            mode = previous_mode if previous_mode in choices else "cool" if "cool" in choices else choices[0] if choices else None
        if mode and mode in modes:
            return Command(domain, "set_hvac_mode", {"entity_id": entity_id, "hvac_mode": mode})
    return None

def media_hold(entity_id,state,attrs):
    if not entity_id.startswith("media_player.") or state in ("unknown","unavailable"):return None
    features=int(attrs.get("supported_features",0))
    if state=="playing" and features&1:return Command("media_player","media_pause",{"entity_id":entity_id})
    if state in ("paused","idle","on","off") and features&16384:return Command("media_player","media_play",{"entity_id":entity_id})
    return None


def summary(entity_id, state, attrs, unit="°C"):
    domain = entity_id.partition(".")[0]
    if state in ("unknown", "unavailable"):
        return "Unavailable", "Check HA connection"
    if domain == "media_player":
        volume = number(attrs.get("volume_level"))
        muted = attrs.get("is_volume_muted") is True
        value = f"{round(volume * 100)}%" if volume is not None and 0 <= volume <= 1 else "--"
        detail = "Muted" if muted else state.title()
        if attrs.get("media_title"):
            detail += " / " + str(attrs["media_title"])
        return value, detail
    if domain == "light":
        value = round(number(attrs.get("brightness"), 0) * 100 / 255)
        modes = set(attrs.get("supported_color_modes", []))
        return (f"{value}%" if state == "on" and modes - {"onoff", "unknown"} else state.title(), "Brightness / press toggles" if modes - {"onoff", "unknown"} else "Press toggles power")
    if domain == "cover":
        position = number(attrs.get("current_position"))
        return (f"{position:g}%" if position is not None else state.title(), "Press stops / " + state)
    if domain == "climate":
        target = number(attrs.get("temperature"))
        low, high = number(attrs.get("target_temp_low")), number(attrs.get("target_temp_high"))
        value = f"{low:g}-{high:g}{unit}" if state == "heat_cool" and low is not None and high is not None else f"{target:g}{unit}" if target is not None else state.title()
        room = number(attrs.get("current_temperature"))
        return value, (f"Room {room:g}{unit} / " if room is not None else "") + state.title()
    if domain == "weather":
        temperature = number(attrs.get("temperature"))
        return f"{temperature:g}{attrs.get('temperature_unit', unit)}" if temperature is not None else "--", state.replace("-", " ").title()
    return "--", "Unsupported entity"
