"""HA owns selection, state and actions. The panel only sends gestures/draws text."""
import asyncio
import json
import logging
import time
from datetime import timedelta,timezone
from homeassistant.components import mqtt
from homeassistant.core import callback,Context
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.event import async_track_state_change_event, async_track_time_interval
from homeassistant.helpers.storage import Store
from homeassistant.helpers.script import Script,async_validate_actions_config
from homeassistant.helpers import config_validation as cv
from homeassistant.util import dt as dt_util
from .const import DEFAULTS, DOMAIN
from .model import Menu, adjust, press, summary, display_text, media_hold
from .audio import PanelAudio
from .device import PanelDevice
from .hardware import PanelHardware
from .app import PanelApp
from .groups import PanelGroups

_LOGGER = logging.getLogger(__name__)
CLOCK = "clock.local"

class KnobController:
    def __init__(self,hass,entry):
        self.hass,self.entry=hass,entry
        self.options={**DEFAULTS,**entry.data,**entry.options}
        self.base=entry.data["mqtt_base"]
        self.targets=list(dict.fromkeys(e for e in self.options["entities"] if e.partition(".")[0] in ("light","cover","climate","media_player")))
        self._saver_weather=self.options["screensaver_weather"] if "screensaver_weather" in entry.options or "screensaver_weather" in entry.data else next((e for e in self.options["entities"] if e.startswith("weather.")),"")
        self._saver_sent=None;self._saver_minute=None
        self._local_route_sent=None
        order=self.options["page_order"]
        self.targets=[e for e in order if e in self.targets]+[e for e in self.targets if e not in order]
        self.menu=Menu(self.targets)
        self.enabled=True;self.online=False;self.error=None
        self.device_id=None
        self._listeners=set();self._unsubs=[];self._tasks=set();self._closed=False
        self._store=Store(hass,1,DOMAIN+"."+entry.entry_id)
        self._lock=asyncio.Lock();self._render_lock=asyncio.Lock()
        self._sent=None;self._led=None;self._menu_at=0;self._pending={};self._dispatch_task=None
        self._projected={};self._previous_modes={};self._forecasts={};self._forecast_at={}
        self._forecast_index=-1;self._weather_task=None;self._clock_minute=None
        self._button_scripts={}
        self.audio=PanelAudio(self)
        self.device=PanelDevice(self)
        self.hardware=PanelHardware(self)
        self.app=PanelApp(self)
        self.groups=PanelGroups(self)

    def spawn(self,coro):
        if self._closed:coro.close();return
        task=self.hass.async_create_task(coro)
        self._tasks.add(task);task.add_done_callback(self._tasks.discard)
        return task

    async def start(self):
        await self.groups.start()
        await self.app.start()
        await self.hardware.start()
        await self.audio.start()
        await self.device.start()
        for button,settings in self.options["buttons"].items():
            for event in ("press","double_press"):
                actions=settings.get(event+"_actions",[])
                if settings.get(event)=="action" and actions:
                    actions=await async_validate_actions_config(self.hass,cv.SCRIPT_SCHEMA(actions))
                    self._button_scripts[(button,event)]=Script(self.hass,actions,f"{self.entry.title} button {button} {event}",DOMAIN,script_mode="queued",max_runs=10)
        saved=await self._store.async_load() or {}
        self.enabled=bool(saved.get("enabled",True))
        if saved.get("selected") in self.targets:self.menu.selected=self.targets.index(saved["selected"])
        self._previous_modes=saved.get("previous_modes",{})
        for topic,handler in [(self.base+"/knob/event",self._mqtt_event),(self.base+"/availability",self._mqtt_available)]:
            self._unsubs.append(await mqtt.async_subscribe(self.hass,topic,handler,1))
        for button in range(1,5):
            self._unsubs.append(await mqtt.async_subscribe(self.hass,self.base+f"/button/{button}/event",self._mqtt_button,1))
        watched=list(dict.fromkeys(self.targets+["sun.sun"]+([self._saver_weather] if self._saver_weather else [])))
        if watched:
            self._unsubs.append(async_track_state_change_event(self.hass,watched,self._state_changed))
        self._unsubs.append(async_track_time_interval(self.hass,self._tick,timedelta(seconds=1)))

    async def stop(self):
        self._closed=True
        await self.groups.stop()
        await self.app.stop()
        await self.hardware.stop()
        await self.audio.stop()
        await self.device.stop()
        self._pending.clear()
        for unsub in self._unsubs:unsub()
        self._unsubs.clear()
        tasks=list(self._tasks)
        for task in tasks:task.cancel()
        if tasks:await asyncio.gather(*tasks,return_exceptions=True)
        for script in self._button_scripts.values():await script.async_unload()
        await self._store.async_save(self._storage())

    def _storage(self):
        return {"enabled":self.enabled,"selected":self.menu.active,"previous_modes":self._previous_modes}

    def save(self):self._store.async_delay_save(self._storage,1)

    @callback
    def add_listener(self,listener):
        self._listeners.add(listener)
        return lambda:self._listeners.discard(listener)

    @callback
    def notify(self):
        for listener in tuple(self._listeners):listener()

    @callback
    def _mqtt_available(self,message):
        if message.payload not in ("online","offline"):return
        self.online=message.payload=="online"
        if not self.online:
            self._pending.clear();self._projected.clear()
            if self._dispatch_task:self._dispatch_task.cancel();self._dispatch_task=None
        self._sent=None;self._led=None;self._saver_sent=None;self._local_route_sent=None;self.notify()
        if self.online:self.spawn(self._online_setup())

    async def _online_setup(self):
        from .discovery import async_retire_discovery
        from .migration import async_cleanup_duplicates
        await async_retire_discovery(self.hass,self.base)
        if self.app.online and self.app.manages_dashboard():await self.app.send_dashboard()
        if self.device_id:await async_cleanup_duplicates(self.hass,self.entry)
        # Configuration only; these messages never operate a relay.
        for button in range(1,5):
            settings=self.options["buttons"].get(str(button),{})
            mapping={"relay1":"1","relay2":"2"}
            payload=",".join(mapping.get(settings.get(g),"0") for g in ("press","double_press"))
            await mqtt.async_publish(self.hass,self.base+f"/button/{button}/binding/set",payload,qos=1,retain=False)
        await self.render_screensaver()
        await self.render()

    @callback
    def _mqtt_button(self,message):
        if message.retain or not self.online:return
        try:
            button=message.topic.rsplit("/",2)[-2]
            data=json.loads(message.payload)
            event=data.get("event_type") if isinstance(data,dict) else None
            if button not in ("1","2","3","4") or event not in ("press","double_press"):return
        except (ValueError,TypeError):return
        self.spawn(self.button_event(button,event))

    async def button_event(self,button,event):
        mode=self.options["buttons"].get(button,{}).get(event,"none")
        if mode in ("none","relay1","relay2"):return # Local relay was already handled on the panel.
        if mode=="action":
            script=self._button_scripts.get((button,event))
            if script:
                await script.async_run({"button":int(button),"gesture":event,"selected_entity":self.menu.active},context=Context())
            return
        if mode=="next":await self.gesture("double_press")
        elif mode=="menu" and self.enabled:
            async with self._lock:
                self.menu.toggle();self._menu_at=time.monotonic();self.notify();await self.render()
        elif mode=="confirm" and self.menu.cursor is not None:await self.gesture("press")
        elif mode=="previous" and self.enabled:
            if self.targets:await self.select(self.targets[(self.menu.selected-1)%len(self.targets)])

    @callback
    def _mqtt_event(self,message):
        if message.retain or not self.enabled or not self.online or not self.hardware.knob_input_online:return
        try:
            data=json.loads(message.payload)
            if not isinstance(data,dict):return
            event=data.get("event_type")
            if data.get("local_handled") is True and event in ("rotate_left","rotate_right","press","long_press"):return
            if event not in ("rotate_left","rotate_right","press","double_press","long_press"):return
        except (ValueError,TypeError):return
        self.spawn(self.gesture(event))

    @callback
    def _state_changed(self,event):
        eid=event.data["entity_id"];state=event.data.get("new_state")
        if state and eid.startswith("climate.") and state.state not in ("off","unavailable","unknown"):
            self._previous_modes[eid]=state.state;self.save()
        if eid==self.menu.active:self.spawn(self.render())
        if eid in (self._saver_weather,"sun.sun"):self.spawn(self.render_screensaver())

    @callback
    def _tick(self,now):
        self.audio.tick()
        self.device.tick()
        if self._saver_minute!=dt_util.now().strftime("%Y%m%d%H%M%z"):
            self.spawn(self.render_screensaver())
        refresh=False
        if self.menu.cursor is not None and time.monotonic()-self._menu_at>=self.options["menu_timeout"]:
            self.menu.cursor=None;self.notify();refresh=True
        for eid,(_,expiry) in list(self._projected.items()):
            if time.monotonic()>=expiry:del self._projected[eid];refresh=True
        if self.menu.active==CLOCK and self._clock_minute!=dt_util.now().strftime("%Y%m%d%H%M"):
            refresh=True
        if self.menu.active and self.menu.active.startswith("weather."):
            self._ensure_forecast(self.menu.active)
        if refresh:self.spawn(self.render())

    async def set_enabled(self,value):
        self.enabled=value;self.menu.cursor=None;self._pending.clear();self._projected.clear()
        if self._dispatch_task:self._dispatch_task.cancel();self._dispatch_task=None
        self.save();self.notify()
        self._sent=None;await self.render()

    async def select(self,entity_id):
        if entity_id not in self.targets:return
        self.menu.selected=self.targets.index(entity_id);self.menu.cursor=None
        self._forecast_index=-1;self.error=None;self.save();self.notify();await self.render()

    async def gesture(self,event):
        async with self._lock:
            if not self.enabled or not self.online:return
            self.error=None
            if event=="long_press" and self.menu.cursor is None and (self.menu.active or "").startswith("media_player."):
                await self._flush()
                state=self.hass.states.get(self.menu.active)
                command=media_hold(self.menu.active,state.state,state.attributes) if state else None
                if command:await self._execute(command)
                else:self.error="Play/pause not supported"
            elif event=="long_press":
                self.menu.toggle();self._menu_at=time.monotonic()
            elif self.menu.cursor is not None:
                self._menu_at=time.monotonic()
                if event=="press":self.menu.confirm();self._forecast_index=-1;self.save()
                elif event in ("rotate_left","rotate_right","double_press"):
                    self.menu.rotate(-1 if event=="rotate_left" else 1)
            elif event=="double_press":
                self.menu.next();self._forecast_index=-1;self.save()
            elif event in ("rotate_left","rotate_right"):
                await self._rotate(-1 if event=="rotate_left" else 1)
            elif event=="press":
                eid=self.menu.active
                if eid and eid.startswith("weather."):self._forecast_index=-1
                elif eid and eid!=CLOCK:
                    await self._flush()
                    state=self.hass.states.get(eid)
                    if state:
                        command=press(eid,state.state,state.attributes,self._previous_modes.get(eid))
                        if command:await self._execute(command)
                        else:self.error="No press action supported"
            self.notify();await self.render()

    async def _rotate(self,delta):
        eid=self.menu.active
        if not eid or eid==CLOCK:return
        if eid.startswith("weather."):
            self._ensure_forecast(eid)
            forecasts=self._forecasts.get(eid,[])
            if forecasts:self._forecast_index=max(-1,min(len(forecasts)-1,self._forecast_index+delta))
            return
        state=self.hass.states.get(eid)
        if not state:self.error="Entity unavailable";return
        projected,expiry=self._projected.get(eid,({},0))
        if time.monotonic()>=expiry:projected={}
        command=adjust(eid,state.state,state.attributes,delta,self.options,projected)
        if not command:self.error="Adjustment not supported";return
        pending={k:v for k,v in command.data.items() if k!="entity_id"}
        if command.domain=="light" and command.service=="turn_off":pending={"brightness_pct":0}
        self._projected[eid]=(pending,time.monotonic()+5)
        self._pending[eid]=command
        # Start with the first detent instead of waiting for rotation to stop.
        # While a service is in flight, retain only the newest target per entity.
        # Projection still accumulates every detent, including direction changes.
        if not self._dispatch_task or self._dispatch_task.done():
            self._dispatch_task=self.spawn(self._dispatch())

    async def _flush(self):
        # A press waits for preceding volume/position changes before operating
        # the same entity; service calls never block receipt of rotary events.
        if self._dispatch_task and not self._dispatch_task.done():
            await self._dispatch_task
        elif self._pending:
            await self._dispatch()

    async def _dispatch(self):
        while self.enabled and self.online and self._pending:
            started=time.monotonic()
            commands=list(self._pending.values());self._pending.clear()
            for command in commands:
                if not self.enabled or not self.online:return
                await self._execute(command)
            # Limit rapid rotary updates without postponing the first action.
            await asyncio.sleep(max(0,.1-(time.monotonic()-started)))

    async def _execute(self,command):
        eid=command.data["entity_id"]
        if eid not in self.options["entities"] or command.domain not in ("climate","light","cover","media_player"):return
        state=self.hass.states.get(eid)
        if not state or state.state in ("unknown","unavailable"):
            self.error="Entity unavailable";self._projected.pop(eid,None);return
        try:
            await self.hass.services.async_call(command.domain,command.service,command.data,blocking=True)
        except HomeAssistantError as err:
            self.error="Action failed - check HA";self._projected.pop(eid,None)
            _LOGGER.warning("Knob action %s.%s failed: %s",command.domain,command.service,err)
        self.notify();await self.render()

    def _ensure_forecast(self,eid):
        if self._closed or not self.enabled:return
        if time.monotonic()-self._forecast_at.get(eid,-10000)<300:return
        if self._weather_task and not self._weather_task.done():return
        self._weather_task=self.spawn(self._get_forecast(eid))

    async def _get_forecast(self,eid):
        self._forecast_at[eid]=time.monotonic()
        state=self.hass.states.get(eid)
        if not state or state.state in ("unknown","unavailable"):return
        features=int(state.attributes.get("supported_features",0))
        kind="hourly" if features&2 else "daily" if features&1 else "twice_daily" if features&4 else None
        if not kind:return
        try:
            response=await self.hass.services.async_call("weather","get_forecasts",{"entity_id":eid,"type":kind},blocking=True,return_response=True)
            self._forecasts[eid]=(response or {}).get(eid,{}).get("forecast",[])
        except HomeAssistantError as err:
            _LOGGER.debug("Forecast unavailable for %s: %s",eid,err)
        await self.render()

    def name(self,eid):
        if eid==CLOCK:return "Clock"
        state=self.hass.states.get(eid)
        name=state.name if state else eid.partition(".")[2].replace("_"," ")
        return display_text(name,32) or display_text(eid.partition(".")[2],32) or "Entity"

    def selection_labels(self):
        names=[self.name(e) for e in self.targets]
        return [f"{i+1}. {name}" for i,name in enumerate(names)]

    def _is_local_media(self,eid):
        if not eid:return False
        from homeassistant.helpers import entity_registry as er
        registry=er.async_get(self.hass)
        return registry.async_get_entity_id("media_player",DOMAIN,self.entry.entry_id+"_audio_player")==eid

    async def _route_local_media(self):
        if not self.hardware.local_media_supported:return
        active=self.enabled and self.menu.cursor is None and self._is_local_media(self.menu.active)
        payload=json.dumps({"v":1,"active":active,"step":int(self.options["volume_step"]),
            "entity":self.menu.active if active else "","title":f"{self.menu.selected+1}/{len(self.targets)} {self.name(self.menu.active)}" if active else ""},separators=(",",":"))
        if payload!=self._local_route_sent:
            await mqtt.async_publish(self.hass,self.base+"/knob/media/set",payload,qos=1,retain=False)
            self._local_route_sent=payload

    def _weather_is_night(self,state,now):
        sun=self.hass.states.get("sun.sun")
        if sun and sun.state in ("above_horizon","below_horizon"):return sun.state=="below_horizon"
        # Providers may expose this even when the Sun integration is not loaded.
        daytime=state.attributes.get("is_daytime") if state else None
        if isinstance(daytime,bool):return not daytime
        try:
            from homeassistant.helpers.sun import is_up
            return not is_up(self.hass,now.astimezone(timezone.utc))
        except (ImportError,AttributeError,ValueError):
            return bool(state and state.state=="clear-night")

    async def render_screensaver(self):
        if not self.online or self._closed:return
        now=dt_util.now()
        if not self.options["screensaver_enabled"]:payload="OFF"
        else:
            state=self.hass.states.get(self._saver_weather)
            condition=state.state if state and state.state not in ("unknown","unavailable") else "unavailable"
            known={"sunny","clear-night","partlycloudy","cloudy","fog","rainy","pouring","snowy","snowy-rainy","hail","lightning","lightning-rainy","windy","windy-variant","exceptional"}
            if condition not in known:condition="unavailable"
            night=self._weather_is_night(state,now)
            if condition in ("sunny","clear-night"):condition="clear-night" if night else "sunny"
            elif night and condition not in ("unavailable","exceptional"):condition+="-night"
            temperature="--"
            if state and condition!="unavailable":
                value=state.attributes.get("temperature")
                if value is not None:temperature=display_text(str(value)+str(state.attributes.get("temperature_unit",self.hass.config.units.temperature_unit)),16)
            payload="\n".join((str(int(now.utcoffset().total_seconds())),self.options["clock_format"],condition,temperature))
        if payload!=self._saver_sent:
            await mqtt.async_publish(self.hass,self.base+"/knob/display/screensaver/set",payload,qos=1,retain=False)
            self._saver_sent=payload
        self._saver_minute=now.strftime("%Y%m%d%H%M%z")

    async def render(self):
        if not self.online or self._closed:return
        async with self._render_lock:
            await self._route_local_media()
            if not self.enabled:return
            eid=self.menu.active
            unavailable=False
            if self.menu.cursor is not None:
                cursor=self.menu.cursor;target=self.targets[cursor]
                title=self.name(target)
                value=f"{cursor+1} / {len(self.targets)}"
                detail="Press selects / hold cancels"
            elif eid==CLOCK:
                now=dt_util.now();self._clock_minute=now.strftime("%Y%m%d%H%M")
                title="Clock"
                value=now.strftime("%H:%M") if self.options["clock_format"]=="24h" else now.strftime("%I:%M %p").lstrip("0")
                detail=now.strftime("%a %d %b %Y")
            elif not eid:
                title,value,detail="Tuya Wall Panel","No entities","Choose entities in HA"
            else:
                state=self.hass.states.get(eid)
                title=f"{self.menu.selected+1}/{len(self.targets)} {self.name(eid)}"
                if not state:value,detail="Unavailable","Check HA entity";unavailable=True
                else:
                    attrs=dict(state.attributes);state_value=state.state
                    unavailable=state_value in ("unknown","unavailable")
                    unit=self.hass.config.units.temperature_unit
                    projected=self._projected.get(eid,({},0))[0]
                    if projected:
                        attrs.update(projected)
                        if eid.startswith("light."):
                            level=projected.get("brightness_pct",0);attrs["brightness"]=level*255/100;state_value="on" if level else "off"
                        if eid.startswith("cover."):attrs["current_position"]=projected["position"]
                    value,detail=summary(eid,state_value,attrs,unit)
                    if projected:detail="Setting... / "+detail
                    if eid.startswith("weather."):
                        self._ensure_forecast(eid)
                        items=self._forecasts.get(eid,[])
                        if 0<=self._forecast_index<len(items):
                            item=items[self._forecast_index]
                            stamp=dt_util.parse_datetime(item.get("datetime",""))
                            when=dt_util.as_local(stamp).strftime("%a %H:%M") if stamp else "Forecast"
                            value=str(item.get("temperature","--"))+attrs.get("temperature_unit",unit)
                            detail=when+" / "+str(item.get("condition","Forecast")).replace("-"," ")
            if self.error:detail=self.error
            frame="\n".join([display_text(title,32),display_text(value,16),display_text(detail,32)])
            kind="frame"
            if self.hardware.media_icons_supported and self.menu.cursor is None and eid and eid.startswith("media_player."):
                state=self.hass.states.get(eid)
                frame+="\n"+("ON" if state and state.state not in ("unknown","unavailable") and state.attributes.get("is_volume_muted") is True else "OFF")
                kind="media_frame"
            if (kind,frame)!=self._sent:
                try:
                    await mqtt.async_publish(self.hass,self.base+"/knob/display/"+kind+"/set",frame,qos=1,retain=False)
                    self._sent=(kind,frame)
                except HomeAssistantError:
                    self._sent=None;return
            if self.options["auto_led"] and self.hardware.values.get("knob_activity_mode")!="ON":
                led="Red" if unavailable else "White"
                if led!=self._led:
                    await mqtt.async_publish(self.hass,self.base+"/knob/led/color/set",led,qos=1,retain=False)
                    self._led=led
