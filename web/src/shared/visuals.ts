import chicken from '../assets/imagery/steamed-chicken.png'
import tofu from '../assets/imagery/vegetable-tofu.png'
import rice from '../assets/imagery/mixed-grain-rice.png'
import meal from '../assets/imagery/meal-table.png'

const dishes:Record<string,string>={'清蒸鸡肉':chicken,'时蔬豆腐':tofu,'杂粮饭':rice}
export function dishIllustration(name:string){
 const source=dishes[name.replace(/[（(]演示[)）]$/,'').trim()]
 return source
  ? {src:source,caption:'菜品示意图 · 实际以供餐为准',alt:`${name}的菜品示意图`}
  : {src:meal,caption:'餐食搭配示意 · 菜品图片待补充',alt:`${name}尚无实拍，展示餐食搭配示意图`}
}
