import {it,expect} from 'vitest'
const sources=import.meta.glob('./**/*.{ts,vue}',{query:'?raw',import:'default',eager:true}) as Record<string,string>
const features=[...new Set(Object.keys(sources).filter(p=>p.startsWith('./features/')).map(p=>p.split('/')[2]!))]
function resolved(file:string,path:string){const parts=file.split('/').slice(0,-1);for(const part of path.split('/')){if(part==='..')parts.pop();else if(part!=='.')parts.push(part)}return parts.join('/')}
it('功能通过公开入口提供路由，应用不导入页面内部文件',()=>{
 expect(Object.keys(sources).some(p=>p.startsWith('./views/'))).toBe(false)
 expect(sources['./router.ts']).not.toMatch(/import\(['"].*\.vue/)
 expect(features.length).toBeGreaterThan(10)
 for(const feature of features){expect(sources[`./features/${feature}/index.ts`],feature).toBeDefined();expect(sources[`./features/${feature}/routes.ts`],feature).toBeDefined()}
})
it('功能之间不能导入内部文件，共享层不依赖功能',()=>{
 for(const [file,text] of Object.entries(sources).filter(([p])=>!p.endsWith('.test.ts'))){
  const owner=file.split('/')
  for(const match of text.matchAll(/(?:from\s*|import\s*\()['"]([^'"]+)['"]/g)){
   if(!match[1]!.startsWith('.'))continue
   const target=resolved(file,match[1]!).split('/')
   if(target[1]!=='features'||owner[1]==='features'&&owner[2]===target[2])continue
   expect(owner[1],file).not.toBe('shared')
   expect(target.slice(3).join('/'),`${file}: ${match[1]}`).toMatch(/^(index(?:\.ts)?)?$/)
  }
 }
})
it('共享表单类型不依赖 Vue 组件或业务功能',()=>{
 const types=sources['./shared/records.ts']!
 expect(types).toBeDefined();expect(types).not.toContain('.vue');expect(types).not.toContain('features/')
 for(const [file,text] of Object.entries(sources).filter(([p])=>p.startsWith('./features/'))){expect(text,file).not.toMatch(/import[^;\n]*\btype\s+(?:Row|Definition|Field)[^;\n]*RecordPage\.vue/)}
})
