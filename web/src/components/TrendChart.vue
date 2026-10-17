<script setup lang="ts">
import{ref,shallowRef,onMounted,onBeforeUnmount,watch}from'vue'
import{init,use,type ECharts}from'echarts/core'
import{LineChart,BarChart}from'echarts/charts'
import{GridComponent,TooltipComponent,LegendComponent}from'echarts/components'
import{CanvasRenderer}from'echarts/renderers'
use([LineChart,BarChart,GridComponent,TooltipComponent,LegendComponent,CanvasRenderer])
const props=defineProps<{kind?:'line'|'bar';labels:string[];series:{name:string;data:(number|null)[]}[]}>(),element=ref<HTMLDivElement>(),chart=shallowRef<ECharts>()
function render(){chart.value?.setOption({color:['--brand-primary','--chart-secondary','--chart-third','--chart-fourth'].map(key=>getComputedStyle(document.documentElement).getPropertyValue(key).trim()),animation:false,tooltip:{trigger:'axis'},legend:{top:0},grid:{left:50,right:25,top:45,bottom:40},xAxis:{type:'category',data:props.labels},yAxis:{type:'value',min:0},series:props.series.map((s,index)=>({...s,type:props.kind??'line',connectNulls:false,smooth:false,...(props.kind==='bar'?{}:{lineStyle:{type:index%2===0?'solid':'dashed',width:2.5},symbol:index%2===0?'circle':'diamond',symbolSize:6})}))},true)}
function resize(){chart.value?.resize()}
onMounted(()=>{chart.value=init(element.value!);render();window.addEventListener('resize',resize)})
watch(()=>[props.labels,props.series,props.kind],render,{deep:true})
onBeforeUnmount(()=>{chart.value?.dispose();window.removeEventListener('resize',resize)})
</script>
<template><div ref="element" style="height:330px;width:100%" role="img" aria-label="趋势统计图"/></template>
