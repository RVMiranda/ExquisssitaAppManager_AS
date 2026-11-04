const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/MACHENIKE/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root=path.resolve(__dirname,'..');
(async()=>{for(const name of ['medio_cuerpo','busto']){
 await sharp(path.join(root,'masters',name+'.svg'),{density:144}).resize(1254,1254).png().toFile(path.join(root,'previews',name+'_vector.png'));
}})();
