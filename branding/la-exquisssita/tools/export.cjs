const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/MACHENIKE/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root=path.resolve(__dirname,'..');
(async()=>{for(const j of JSON.parse(fs.readFileSync(path.join(root,'work/render_jobs.json')))){
 fs.mkdirSync(path.dirname(j.dest),{recursive:true});
 let im=sharp(j.src,{density:192}).resize(j.size,j.size);
 if(j.bg)im=im.flatten({background:j.bg});
 await im.ensureAlpha().withIccProfile('srgb').png({palette:false,compressionLevel:9}).toFile(j.dest);
}console.log('All SVG exports rendered');})();
