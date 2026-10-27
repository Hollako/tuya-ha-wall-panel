"""Publish this repository's prepared release using Git's configured credential helper.

Credentials stay in memory and are never printed or saved. No panel access.
"""
from pathlib import Path
import argparse,json,os,subprocess,urllib.request,urllib.error

REPO='Hollako/tuya-ha-wall-panel'
API='https://api.github.com/repos/'+REPO

def auth():
    environment=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='never')
    result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',capture_output=True,text=True,env=environment,timeout=30)
    if result.returncode:raise RuntimeError('No cached GitHub credential available through Git credential manager')
    values=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    if not values.get('password'):raise RuntimeError('Git credential helper returned no GitHub token')
    return values['password']

def request(token,url,method='GET',body=None,content_type='application/json'):
    headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'Tuya-Wall-Panel-Release','Content-Type':content_type}
    data=body if isinstance(body,bytes) else json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(url,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=180) as response:return json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError('GitHub request failed with HTTP '+str(error.code)+' for '+url.split('?')[0]) from None

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true');parser.add_argument('--tag',default='v1.18.0')
    parser.add_argument('--assets',type=Path,default=Path('.publish/v1.18.0'));parser.add_argument('--publish',action='store_true')
    args=parser.parse_args();token=auth();repository=request(token,API)
    print(json.dumps({'repository':repository['full_name'],'default_branch':repository['default_branch'],'can_push':repository.get('permissions',{}).get('push',False)}),flush=True)
    releases=request(token,API+'/releases?per_page=10')
    if args.check:
        print(json.dumps([{'tag':r['tag_name'],'url':r['html_url'],'draft':r['draft'],'assets':[a['name'] for a in r['assets']]} for r in releases],indent=2));return
    files=sorted(path for path in args.assets.iterdir() if path.is_file() and path.name!='release-notes.md')
    if not files:raise RuntimeError('No prepared release files')
    release=next((r for r in releases if r['tag_name']==args.tag),None)
    if release is None:
        revision=subprocess.run(['git','rev-parse','HEAD'],capture_output=True,text=True,check=True).stdout.strip()
        release=request(token,API+'/releases','POST',dict(tag_name=args.tag,target_commitish=revision,name='Tuya HA Wall Panel 1.18.0 · Android 0.23.0 / Audio Receiver 1.2.0',body=(args.assets/'release-notes.md').read_text(encoding='utf-8'),draft=True,prerelease=False))
    elif not release['draft']:
        raise RuntimeError('This release is already public; refusing to overwrite its assets')
    existing={asset['name']:asset for asset in release['assets']}
    from urllib.parse import quote
    for path in files:
        if path.name in existing:
            if existing[path.name]['size']!=path.stat().st_size:raise RuntimeError('Different asset already uploaded: '+path.name)
            print('Already uploaded:',path.name,flush=True);continue
        url=release['upload_url'].split('{')[0]+'?name='+quote(path.name)
        mime='application/vnd.android.package-archive' if path.suffix=='.apk' else 'application/zip' if path.suffix=='.zip' else 'application/gzip' if path.name.endswith('.gz') else 'text/plain'
        uploaded=request(token,url,'POST',path.read_bytes(),mime)
        if uploaded['size']!=path.stat().st_size:raise RuntimeError('Upload size mismatch: '+path.name)
        print('Uploaded:',path.name,uploaded['size'],flush=True)
    if args.publish:
        release=request(token,API+'/releases/'+str(release['id']),'PATCH',{'draft':False,'make_latest':'true'})
    print(json.dumps({'url':release['html_url'],'draft':release['draft']}),flush=True)

if __name__=='__main__':main()
