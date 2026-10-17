import {http} from '../api/http'
import type {Row} from '../shared/records'
export function csvText(headers:string[],rows:unknown[][]):string{
 const cell=(value:unknown)=>{let text=String(value??'');if(/^[\s]*[=+\-@]/.test(text)||/^[\t\r\n]/.test(text))text="'"+text;return '"'+text.replaceAll('"','""')+'"'}
 return '\uFEFF'+[headers,...rows].map(row=>row.map(cell).join(',')).join('\r\n')
}
export async function exportRows(endpoint:string,filters:Row={},filterArray?:(row:Row)=>boolean):Promise<Row[]>{
 const rows:Row[]=[]
 for(let page=1;;page++){const data=await http.get<never,any>(endpoint,{params:{...filters,page,size:100}});if(Array.isArray(data))return filterArray?data.filter(filterArray):data;rows.push(...data.records);if(!data.records.length||data.total==null||rows.length>=data.total)return rows}
}
export function downloadCsv(name:string,headers:string[],rows:unknown[][]){
 const url=URL.createObjectURL(new Blob([csvText(headers,rows)],{type:'text/csv;charset=utf-8'})),a=document.createElement('a');a.href=url;a.download=name+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),0)
}
