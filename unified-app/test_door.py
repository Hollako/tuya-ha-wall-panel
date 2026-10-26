"""Production door client against a local fake station only; no physical relays."""
import http.server,threading,subprocess,tempfile,re,hashlib
from pathlib import Path
r=Path(__file__).resolve().parent
class Station(http.server.BaseHTTPRequestHandler):
 mode='success';requests=0;accepted=0
 def log_message(self,*args):pass
 def do_PUT(self):
  Station.requests+=1;body=self.rfile.read(int(self.headers['Content-Length']));assert b'<cmd>open</cmd>' in body and b'alwaysOpen' not in body
  auth=self.headers.get('Authorization','')
  if not auth:
   self.send_response(401);self.send_header('WWW-Authenticate','Digest realm="test", nonce="nonce", algorithm=MD5, qop="auth"');self.send_header('Content-Length','0');self.end_headers();return
  fields=dict(re.findall(r'(\w+)="([^"]*)"',auth));H=lambda s:hashlib.md5(s.encode()).hexdigest();expected=H(H('admin:test:secret')+':nonce:00000001:'+fields['cnonce']+':auth:'+H('PUT:'+self.path));assert fields['response']==expected
  Station.accepted+=1
  if Station.mode=='redirect':
   self.send_response(302);self.send_header('Location','http://127.0.0.1:1/redirect');self.send_header('Content-Length','0');self.end_headers();return
  data=b'<ResponseStatus><statusCode>'+ (b'1' if Station.mode=='success' else b'4')+b'</statusCode></ResponseStatus>';self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Station);threading.Thread(target=server.serve_forever,daemon=True).start()
java=r'C:\\Program Files (x86)\\Java\\jre1.8.0_431\\bin\\java.exe'
body='''package local.wallpanel.app;public class DoorTests {public static void main(String[] a)throws Exception{SipConfig c=new SipConfig();c.server="127.0.0.1";c.user="1001";c.stationBrand=a[1].equals("generic")?"generic":"hikvision";c.doorServer=a[0];c.doorUser="admin";c.doorPassword="secret";int door=1;if(a[1].equals("disabled"))door=2;boolean expected=a[1].equals("success"),ok=false;final boolean cancel=a[1].equals("cancel");try{IntercomDoor.open(c,door,new IntercomDoor.Guard(){int n;public boolean allowed(){return !cancel||++n<=2;}});ok=true;}catch(java.io.IOException e){}if(ok!=expected)throw new AssertionError(a[1]);}}'''
try:
 with tempfile.TemporaryDirectory(dir=r/'test-classes',prefix='door-') as tmp:
  out=Path(tmp);f=out/'DoorTests.java';f.write_text(body);src=r/'src/local/wallpanel/app'
  subprocess.run([java,'-jar',str(r.parent/'audio/tooling/ecj.jar'),'-1.7','-nowarn','-d',str(out),*[str(src/n) for n in ['SipConfig.java','SipDigest.java','IntercomDoor.java','HikvisionDoor.java']],str(f)],check=True)
  for mode,requests,accepted in [('success',2,1),('disabled',0,0),('generic',0,0),('cancel',1,0),('redirect',2,1),('rejected',2,1)]:
   Station.mode=mode;Station.requests=Station.accepted=0
   subprocess.run([java,'-cp',str(out),'local.wallpanel.app.DoorTests','http://127.0.0.1:'+str(server.server_port),mode],check=True,timeout=10)
   assert (Station.requests,Station.accepted)==(requests,accepted),(mode,Station.requests,Station.accepted)
 print('PASS: production door digest auth, exact momentary-open path/body, generic brand sends no requests, disabled Door2, ended-call rejection, no redirects/replay and response confirmation. Fake station only.')
finally:server.shutdown();server.server_close()
