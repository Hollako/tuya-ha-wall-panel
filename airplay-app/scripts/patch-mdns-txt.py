from pathlib import Path
import sys
p=Path(sys.argv[1]);s=p.read_text()
if 'wpar_create_txt' not in s:
 marker='void rr_add_txt(struct rr_entry *rr_txt, const char *txt) {'
 helper='''/* DNS TXT character-strings allow 255 bytes; DNS name labels allow 63.
 * AirPlay public-key TXT entries exceed 63 and must not use create_label. */
static uint8_t *wpar_create_txt(const char *txt) {
 size_t n=strlen(txt);if(n>255)die("TXT entry too long");
 uint8_t *data=malloc(n+2);if(!data)die("No memory for TXT");
 data[0]=(uint8_t)n;memcpy(data+1,txt,n);data[n+1]=0;return data;
}
'''
 assert marker in s;s=s.replace(marker,helper+marker)
 s=s.replace('txt_rec->txt = create_label(txt);','txt_rec->txt = wpar_create_txt(txt);')
 p.write_text(s)
