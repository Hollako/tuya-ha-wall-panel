"""Opaque per-panel media IDs; local songs play on panels without an HA audio relay."""
import re
from urllib.parse import urlsplit,parse_qs
from homeassistant.components.media_player import BrowseMedia,MediaClass,MediaType
from homeassistant.components import media_source
from homeassistant.exceptions import HomeAssistantError

def local_id(node,track):return f"wallpanel-local://{node}/{track}"
def track_id(value):
    uri=urlsplit(value)
    if uri.scheme!="wallpanel-local" or not re.fullmatch(r"panel-[A-Za-z0-9-]{8,80}",uri.netloc) or not re.fullmatch(r"/audio-[1-9][0-9]{0,17}",uri.path) or uri.query or uri.fragment:raise HomeAssistantError("Invalid panel music selection")
    return uri.netloc,uri.path[1:]
def folder_id(node,folder,offset=0):return f"wallpanel-library://{node}/{folder}?offset={offset}"
async def browse(player,node,media_content_id=None):
    if media_content_id and media_source.is_media_source_id(media_content_id):return await media_source.async_browse_media(player.hass,media_content_id,content_filter=lambda item:item.media_content_type.startswith("audio/"))
    if not media_content_id or media_content_id=="wallpanel-root":
        def entry(title,id,type):return BrowseMedia(title=title,media_class=MediaClass.DIRECTORY,media_content_type=type,media_content_id=id,can_play=False,can_expand=True)
        return BrowseMedia(title="Music sources",media_class=MediaClass.DIRECTORY,media_content_type="library",media_content_id="wallpanel-root",can_play=False,can_expand=True,children=[entry(player.groups.panels.get(node,{}).get("name","Wall Panel")+" local music",folder_id(node,"root"),"wallpanel_library"),entry("Home Assistant media","media-source://","media_source")])
    folder="root";offset=0
    if media_content_id:
        uri=urlsplit(media_content_id);folder=uri.path.lstrip("/")
        try:offset=int(parse_qs(uri.query).get("offset",["0"])[0])
        except ValueError as error:raise HomeAssistantError("Invalid music page") from error
        if uri.scheme!="wallpanel-library" or uri.netloc!=node or uri.fragment or not re.fullmatch(r"root|all|folder-[0-9a-f]{32}",folder) or not 0<=offset<=10000:raise HomeAssistantError("Choose a folder from this panel's music library")
    library=await player.groups.library(node,folder,offset);children=[]
    def directory(title,id,type="wallpanel_library"):
        return BrowseMedia(title=title,media_class=MediaClass.DIRECTORY,media_content_type=type,media_content_id=id,can_play=False,can_expand=True)
    if folder=="root":
        children.append(directory("All music",folder_id(node,"all")))
        children.extend(directory(f"{f['name']} ({f['count']})",folder_id(node,f["id"])) for f in library["folders"])
        children.append(directory("Home Assistant media","media-source://","media_source"))
    for song in library["tracks"]:
        children.append(BrowseMedia(title=song["title"]+(" — "+song["artist"] if song.get("artist") else ""),media_class=MediaClass.TRACK,media_content_type="wallpanel_local",media_content_id=local_id(node,song["id"]),can_play=True,can_expand=False))
    if library.get("nextOffset") is not None:children.append(directory("More tracks",folder_id(node,folder,library["nextOffset"])))
    title=player.groups.panels.get(node,{}).get("name","Wall Panel")+" local music"
    return BrowseMedia(title=title,media_class=MediaClass.DIRECTORY,media_content_type="wallpanel_library",media_content_id=folder_id(node,folder,offset),can_play=False,can_expand=True,children=children)

async def play(player,leader,members,media_id,kwargs):
    if kwargs.get("announce") or kwargs.get("enqueue") not in (None,"replace"):raise HomeAssistantError("Group queues and announcements are not supported yet")
    if media_id.startswith("wallpanel-local:"):
        node,track=track_id(media_id)
        if node!=leader:raise HomeAssistantError("Select music stored on the source panel")
        await player.groups.command("play_track",leader=leader,members=members,track=track);return
    if media_source.is_media_source_id(media_id):media_id=(await media_source.async_resolve_media(player.hass,media_id,player.entity_id)).url
    from homeassistant.components.media_player import async_process_play_media_url
    media_id=async_process_play_media_url(player.hass,media_id)
    if not media_id.startswith(("http://","https://")):raise HomeAssistantError("Choose local panel music or an HTTP/HTTPS audio source")
    await player.groups.command("play_source",leader=leader,members=members,source=media_id,title=(kwargs.get("extra") or {}).get("title",""))
