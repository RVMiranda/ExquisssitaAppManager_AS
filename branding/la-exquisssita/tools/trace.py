from pathlib import Path
import sys, shutil, json, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/vendor'))
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
import vtracer
for d in ['references','masters','work','exports','previews','reports']:
    (ROOT/d).mkdir(exist_ok=True)
sources={'medio_cuerpo':'exec-61a0c556-09a0-412d-9da4-342c03f1a273.png','busto':'exec-b2df9c46-658b-4668-9aff-6203e722ed54.png'}
pal=['#3D2723','#FAF3E0','#E5C2A4','#7E392F','#A07324','#B8860B']
centers=np.array([[66,30,19],[251,246,226],[240,193,151],[137,57,40],[164,113,29],[184,134,11]],dtype=float)
def trace(mask,name):
    mask=ndi.gaussian_filter(mask.astype(float),0.65)>0.5
    src=ROOT/'work'/f'{name}.png'; out=src.with_suffix('.svg')
    Image.fromarray(np.where(mask,0,255).astype('uint8')).convert('RGB').save(src)
    vtracer.convert_image_to_svg_py(str(src),str(out),colormode='binary',mode='spline',filter_speckle=20,corner_threshold=90,length_threshold=1.5,max_iterations=10,splice_threshold=20,path_precision=2)
    return list(ET.parse(out).getroot())
for name,file in sources.items():
    src=Path('C:/Users/MACHENIKE/.codex/generated_images/01a17f60-f077-70a0-abaf-f9f8bf300a43')/file
    shutil.copy2(src,ROOT/'references'/f'{name}_aprobado.png')
    rgb=np.asarray(Image.open(src).convert('RGB')).astype(float); h,w=rgb.shape[:2]
    # Gold is reserved for the flower center; ochre leaves share similar source tones.
    labels=((rgb[:,:,None,:]-centers[None,None,:5,:])**2).sum(3).argmin(2)
    seed=np.zeros((h,w),bool);seed[0,:]=seed[-1,:]=True;seed[:,0]=seed[:,-1]=True
    bg=ndi.binary_propagation(seed & (labels==1),mask=labels==1)
    silhouette=~bg
    # Remove disconnected raster dust, retain all intentional connected geometry.
    components,n=ndi.label(silhouette); counts=np.bincount(components.ravel()); counts[0]=0
    silhouette=components==counts.argmax()
    # Assign flower center to gold by spatial component, not color globally.
    ochres,n=ndi.label(labels==4)
    for i in range(1,n+1):
        ys,xs=np.where(ochres==i)
        if len(xs)>50 and xs.mean()<w*.4 and ys.mean()<h*.6: labels[ochres==i]=5
        elif name=='medio_cuerpo' and len(xs)>200 and ys.mean()>h*.6: pass
        else:
            region=ochres==i
            labels[region]=((rgb[region,None,:]-centers[None,:4,:])**2).sum(2).argmin(1)
    layers=[]
    for k,mask in [(0,silhouette)]+[(k,(labels==k)&silhouette) for k in range(1,6)]:
        if not mask.any():continue
        for p in trace(mask,f'{name}_{k}'):
            p.tag=p.tag.split('}')[-1];p.set('fill',pal[k]);p.set('fill-rule','evenodd')
            layers.append(ET.tostring(p,encoding='unicode'))
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">\n<title>La Exquisssita — {name}</title>\n'+ '\n'.join(layers)+'\n</svg>'
    (ROOT/'masters'/f'{name}.svg').write_text(svg,encoding='utf-8')
    # Separate one-ink drawing: only original dark contours and dark facial features.
    mono=trace((labels==0)&silhouette,f'{name}_mono')
    for p in mono:p.tag=p.tag.split('}')[-1];p.set('fill','#000000');p.set('fill-rule','evenodd')
    (ROOT/'masters'/f'{name}_monocromo.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">'+''.join(ET.tostring(p,encoding='unicode') for p in mono)+'</svg>',encoding='utf-8')
    ys,xs=np.where(silhouette)
    meta={'width':w,'height':h,'bbox':[int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)]}
    cx=(xs.min()+xs.max())/2;cy=(ys.min()+ys.max())/2
    meta['center']=[float(cx),float(cy)];meta['radius']=float(np.sqrt((xs-cx)**2+(ys-cy)**2).max())
    (ROOT/'work'/f'{name}.json').write_text(json.dumps(meta))
    print(name,len(layers),'paths',meta)


