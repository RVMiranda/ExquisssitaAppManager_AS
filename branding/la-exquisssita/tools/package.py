from pathlib import Path
import json,re,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]; RES=ROOT.parents[1]/'app/src/main/res'
jobs=[]
def put(path,text):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')
def svg(name):return ET.parse(ROOT/'masters'/f'{name}.svg').getroot()
def content(name):return ''.join(ET.tostring(p,encoding='unicode').replace('ns0:','').replace(':ns0','') for p in svg(name))
def wrap(name,canvas,scale,tx,ty,bg=None):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{canvas}" height="{canvas}" viewBox="0 0 {canvas} {canvas}">'+(f'<path fill="{bg}" d="M0 0H{canvas}V{canvas}H0Z"/>' if bg else '')+f'<g transform="translate({tx} {ty}) scale({scale})">'+content(name)+'</g></svg>'
def vd(name,size,scale=1,tx=0,ty=0,viewport=1254):
    out=f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n<group android:translateX="{tx}" android:translateY="{ty}" android:scaleX="{scale}" android:scaleY="{scale}">\n'
    for p in svg(name):
        if p.tag.split('}')[-1]!='path':continue
        transform=p.get('transform','translate(0,0)');x,y=re.findall(r'[-\d.]+',transform)
        out+=f'<group android:translateX="{x}" android:translateY="{y}"><path android:fillColor="{p.get("fill")}" android:fillType="evenOdd" android:pathData="{p.get("d")}"/></group>\n'
    return out+'</group>\n</vector>\n'
def job(src,dest,size,bg=None):jobs.append({'src':str(src),'dest':str(dest),'size':size,'bg':bg})
for name in ['medio_cuerpo','busto']:
    job(ROOT/'masters'/f'{name}.svg',ROOT/'exports'/f'{name}_2048.png',2048)
    put(RES/'drawable'/f'exquisssita_{name}.xml',vd(name,280 if name=='medio_cuerpo' else 256))
job(ROOT/'masters/medio_cuerpo.svg',RES/'drawable-nodpi/exquisssita_bienvenida_1120.png',1120)
meta=json.loads((ROOT/'work/busto.json').read_text());cx,cy=meta['center'];radius=meta['radius']+2
scale=32.5/radius;tx=54-cx*scale;ty=54-cy*scale
for suffix,source in [('foreground','busto'),('monochrome','busto_monocromo')]:
    put(ROOT/'masters'/f'launcher_{suffix}.svg',wrap(source,108,scale,tx,ty))
    if suffix=='foreground':put(RES/'drawable'/f'exquisssita_launcher_{suffix}.xml',vd(source,108,scale,tx,ty,108))
    job(ROOT/'masters'/f'launcher_{suffix}.svg',ROOT/'previews'/f'launcher_{suffix}.png',432)
for density,size in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    job(ROOT/'masters/launcher_monochrome.svg',RES/f'drawable-{density}/exquisssita_launcher_monochrome.png',size)
put(RES/'drawable/exquisssita_launcher_background.xml','<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#FAF3E0"/></shape>')
for version in [26,33]:
    xml='<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/exquisssita_launcher_background"/><foreground android:drawable="@drawable/exquisssita_launcher_foreground"/>'
    if version==33:xml+='<monochrome android:drawable="@drawable/exquisssita_launcher_monochrome"/>'
    xml+='</adaptive-icon>'
    for suffix in ['','_round']:put(RES/f'mipmap-anydpi-v{version}'/f'exquisssita_launcher{suffix}.xml',xml)
# Legacy icons receive their own full canvas. Radius 46% keeps all artwork inside a circle.
s=46/radius
put(ROOT/'masters/launcher_legacy.svg',wrap('busto',100,s,50-cx*s,50-cy*s,'#FAF3E0'))
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    job(ROOT/'masters/launcher_legacy.svg',RES/f'mipmap-{density}/exquisssita_launcher.png',size)
    job(ROOT/'masters/launcher_legacy.svg',RES/f'mipmap-{density}/exquisssita_launcher_round.png',size)
# Play image is square and fully opaque; same artwork margins as legacy.
job(ROOT/'masters/launcher_legacy.svg',ROOT/'exports/google_play_512.png',512,'#FAF3E0')
# Android 12+ splash without icon background: 288dp canvas, artwork within 192dp circle.
s=95/radius
put(ROOT/'masters/splash_sistema.svg',wrap('busto',288,s,144-cx*s,144-cy*s))
put(RES/'drawable/exquisssita_splash_sistema.xml',vd('busto',288,s,144-cx*s,144-cy*s,288))
job(ROOT/'masters/splash_sistema.svg',ROOT/'previews/splash_sistema_288.png',288)
for size in [16,32,48,64,192]:job(ROOT/'masters/busto.svg',ROOT/'previews'/f'busto_{size}.png',size)
job(ROOT/'masters/medio_cuerpo.svg',ROOT/'previews/medio_cuerpo_256.png',256)
put(ROOT/'work/render_jobs.json',json.dumps(jobs,indent=2))
print(len(jobs),'exports; Android vectors and adaptive XML prepared')
