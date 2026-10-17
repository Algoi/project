export type Row=Record<string,any>
export type Field={key:string;label:string;type?:string;source?:string;options?:{value:any;label:string}[];initial?:any;purpose?:string}
export type Definition={title:string;endpoint:string;columns:[string,string][];fields?:Field[];detail?:string;search?:string;createOnly?:boolean;save?:(body:Row,id:number|null)=>Promise<any>}
