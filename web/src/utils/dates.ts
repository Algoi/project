export function day(offset=0){const shanghai=new Date(Date.now()+8*3600000);shanghai.setUTCDate(shanghai.getUTCDate()+offset);return shanghai.toISOString().slice(0,10)}
