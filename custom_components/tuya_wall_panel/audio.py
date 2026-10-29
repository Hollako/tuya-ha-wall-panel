"""Audio state and commands, independent of knob entity control being enabled."""
import json
import math
import time
import uuid
import base64
import hashlib
import re
import binascii
from datetime import datetime,timezone
from homeassistant.components import mqtt
from homeassistant.core import callback
from homeassistant.exceptions import HomeAssistantError

LOCAL_PLAYBACK="Local Playback"
LEGACY_LOCAL_PLAYBACK="Android playback"

def source_name(value):
    """Display name; keep the established panel command identifier compatible."""
    return LOCAL_PLAYBACK if value==LEGACY_LOCAL_PLAYBACK else value

class PanelAudio:
    def __init__(self,controller):
        self.controller=controller;self.online=False;self.data={};self._unsubs=[];self.position_updated_at=None
        self._reported={};self._pending={}
        self.owner="legacy"
        self.artwork_id="";self.artwork=None

    async def start(self):
        for suffix,handler in [("availability",self._availability),("state",self._state),("owner",self._owner),("artwork",self._artwork)]:
            self._unsubs.append(await mqtt.async_subscribe(self.controller.hass,self.controller.base+"/audio/"+suffix,handler,1))

    async def stop(self):
        for unsub in self._unsubs:unsub()
        self._unsubs.clear()
        self._pending.clear()
        self.artwork=None;self.artwork_id=""

    @callback
    def _artwork(self,message):
        try:
            if not isinstance(message.payload,str) or len(message.payload)>100000:return
            data=json.loads(message.payload)
            if not isinstance(data,dict):return
            identity=data.get("id");encoded=data.get("image")
            if identity=="" and encoded=="":self.artwork=None;self.artwork_id="";self.controller.notify();return
            if not isinstance(identity,str) or not re.fullmatch(r"[0-9a-f]{64}",identity) or not isinstance(encoded,str) or data.get("content_type")!="image/jpeg":return
            picture=base64.b64decode(encoded,validate=True)
            if not 4<=len(picture)<=65536 or not picture.startswith(b"\xff\xd8") or not picture.endswith(b"\xff\xd9") or hashlib.sha256(picture).hexdigest()!=identity:return
            self.artwork=picture;self.artwork_id=identity;self.controller.notify()
        except (ValueError,TypeError,binascii.Error):return

    @property
    def current_artwork(self):
        return self.artwork if self.online and self.data.get("source")=="AirPlay" and self.artwork_id and self.artwork_id==self.data.get("artwork_id") else None

    @callback
    def _owner(self,message):
        if message.payload not in ("legacy","app"):return
        self.owner=message.payload;self.controller.notify()

    @callback
    def _availability(self,message):
        self.online=message.payload=="online"
        if not self.online:self._pending.clear();self.data=dict(self._reported)
        self.controller.notify()

    @callback
    def _state(self,message):
        try:
            data=json.loads(message.payload)
            if not isinstance(data,dict) or data.get("state") not in ("off","idle","playing","paused","buffering"):return
            if data.get("source") not in (LEGACY_LOCAL_PLAYBACK,LOCAL_PLAYBACK,"Line Input","AirPlay"):return
            sources=data.get("sources")
            if sources is not None:
                if not isinstance(sources,list) or not 1<=len(sources)<=3:return
                sources=[source_name(source) for source in sources]
                if any(source not in (LOCAL_PLAYBACK,"Line Input","AirPlay") for source in sources) or len(set(sources))!=len(sources):return
                data["sources"]=sources
            data["source"]=source_name(data["source"])
            if "volume_scope" in data:data["volume_scope"]=source_name(data["volume_scope"])
            volume=data.get("volume")
            if isinstance(volume,bool) or not isinstance(volume,(int,float)) or not math.isfinite(volume) or not 0<=volume<=1:return
            if not isinstance(data.get("muted"),bool):return
            old_session=self._reported.get("audio_session")
            if old_session and data.get("audio_session")!=old_session:self._pending.clear()
            requests={pending[2] for pending in self._pending.values()}
            for request_id in requests:
                fields={field:pending[0] for field,pending in self._pending.items() if pending[2]==request_id}
                confirmed=all(data.get(field)==wanted for field,wanted in fields.items())
                rejected=data.get("request_id")==request_id and data.get("error")
                if confirmed or rejected:
                    self._pending={field:pending for field,pending in self._pending.items() if pending[2]!=request_id}
            self._reported=dict(data);self.position_updated_at=datetime.now(timezone.utc)
            self._project();self.controller.notify()
        except (ValueError,TypeError):return

    def _project(self):
        now=time.monotonic()
        self._pending={field:pending for field,pending in self._pending.items() if pending[1]>now}
        self.data={**self._reported,**{field:pending[0] for field,pending in self._pending.items()}}

    @callback
    def tick(self):
        if self._pending:
            before=self.data;self._project()
            if self.data!=before:self.controller.notify()

    async def command(self,command,**values):
        if not self.online or not self.controller.online:raise HomeAssistantError("Tuya Wall Panel audio is offline")
        projected={};request_id=None
        if command in ("receiver_delay","receiver_trim"):
            value=values.get("value");minimum,maximum=(-500,500) if command=="receiver_delay" else (0,100)
            if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or value!=int(value) or not minimum<=value<=maximum:
                raise HomeAssistantError("Invalid AirPlay timing or trim")
        elif command=="receiver_enabled" and not isinstance(values.get("value"),bool):raise HomeAssistantError("AirPlay receiver requires true or false")
        if command=="volume":
            value=values.get("value")
            if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or not 0<=value<=1:
                raise HomeAssistantError("Volume must be between 0 and 1")
            steps=self._reported.get("volume_steps")
            wanted=math.floor(value*steps+.5)/steps if isinstance(steps,int) and not isinstance(steps,bool) and 1<=steps<=1000 else value
            projected={"volume":wanted,"muted":False}
        elif command=="mute":
            if not isinstance(values.get("value"),bool):raise HomeAssistantError("Mute must be true or false")
            projected={"muted":values["value"]}
        if projected:
            request_id=uuid.uuid4().hex;deadline=time.monotonic()+4
            for field,wanted in projected.items():self._pending[field]=(wanted,deadline,request_id)
            self._project();self.controller.notify();values["request_id"]=request_id
        try:
            await mqtt.async_publish(self.controller.hass,self.controller.base+"/audio/command/set",json.dumps({"command":command,**values},separators=(",",":")),qos=1,retain=False)
        except Exception:
            if request_id:
                self._pending={field:pending for field,pending in self._pending.items() if pending[2]!=request_id}
                self._project();self.controller.notify()
            raise
