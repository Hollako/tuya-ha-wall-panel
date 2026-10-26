"""Generate original wall-panel vector artwork and PNG brand sizes."""
from pathlib import Path
from PIL import Image,ImageDraw

root=Path(__file__).resolve().parent.parent
brand=root/'custom_components/tuya_wall_panel/brand';brand.mkdir(parents=True,exist_ok=True)
svg='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
<rect width="256" height="256" rx="30" fill="#172b43"/>
<rect x="13" y="20" width="164" height="216" rx="17" fill="#0b1728"/>
<rect x="25" y="32" width="140" height="142" rx="12" fill="#0d7490"/>
<path d="M49 102 L95 62 L141 102 M60 94 V145 H130 V94" fill="none" stroke="#ffffff" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M89 145 V119 H105 V145" fill="none" stroke="#ffffff" stroke-width="8"/>
<rect x="27" y="188" width="62" height="13" rx="6" fill="#22d3c5"/>
<rect x="27" y="211" width="111" height="9" rx="4" fill="#53718f"/>
<circle cx="217" cy="128" r="28" fill="#091623" stroke="#22d3c5" stroke-width="6"/>
<path d="M217 108 V114" stroke="#ffffff" stroke-width="5" stroke-linecap="round"/>
<g fill="#61b8ee"><rect x="201" y="25" width="32" height="9" rx="4"/><rect x="201" y="61" width="32" height="9" rx="4"/><rect x="201" y="186" width="32" height="9" rx="4"/><rect x="201" y="222" width="32" height="9" rx="4"/></g>
</svg>'''
(brand/'icon.svg').write_text(svg)
# Rasterize the same simple geometry without external rendering dependencies.
scale=4
im=Image.new('RGBA',(256*scale,256*scale),(0,0,0,0));d=ImageDraw.Draw(im)
def box(coords,fill,radius=0,outline=None,width=1):
    d.rounded_rectangle(tuple(x*scale for x in coords),radius*scale,fill=fill,outline=outline,width=width*scale)
def line(coords,fill,width):d.line([(x*scale,y*scale) for x,y in coords],fill=fill,width=width*scale,joint='curve')
box((0,0,255.75,255.75),'#172b43',30);box((13,20,177,236),'#0b1728',17);box((25,32,165,174),'#0d7490',12)
line([(49,102),(95,62),(141,102)],'white',10);line([(60,94),(60,145),(130,145),(130,94)],'white',10)
line([(89,145),(89,119),(105,119),(105,145)],'white',8)
box((27,188,89,201),'#22d3c5',6);box((27,211,138,220),'#53718f',4)
d.ellipse((189*scale,100*scale,245*scale,156*scale),fill='#091623',outline='#22d3c5',width=6*scale)
line([(217,108),(217,114)],'white',5)
for y in (25,61,186,222):box((201,y,233,y+9),'#61b8ee',4)
for size,name in ((256,'icon.png'),(512,'icon@2x.png')):
    im.resize((size,size),Image.Resampling.LANCZOS).save(brand/name,optimize=True)
print('Original vector icon and transparent 256/512 PNGs generated.')
