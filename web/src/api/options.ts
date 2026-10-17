import {http} from './http'
import type {Row} from '../shared/records'
/** Fetch complete authorized options for pages whose selected row supplies business context. */
export async function allOptions(source:string):Promise<Row[]>{
 const rows:Row[]=[]
 for(let page=1;;page++){
  const data=await http.get<never,any>(source,{params:{page,size:100}})
  if(Array.isArray(data))return data
  rows.push(...data.records)
  if(!data.records.length||data.total==null||rows.length>=data.total)return rows
 }
}
