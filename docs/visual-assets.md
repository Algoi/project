# 网站图片与浅绿色视觉规范

日期：2026-10-17。使用内置 `image_gen` 生成新图片，未使用 CLI/API 密钥模式。所有交付图片已复制到项目 `web/src/assets/imagery/`；原始生成文件保留在 Codex 的 generated_images 目录。人物和餐食为生成的场景示意，不对应真实网点拍摄或真实供餐记录。

## 主色规范

大面积主视觉采用浅绿色 `#e5efdf`，辅助底色 `#f0f5eb`，米白页面 `#f6f8f2`，米白卡片 `#fffefa`。文字深绿 `#294a35`，主要按钮和链接为绿色 `#3f6b4e`，次要文字为 `#596d59`。统一变量位于 `web/src/style.css`，图表读取同一组绿色变量，并以实线/虚线区分数据。

主要按钮白字与绿色背景的对比度约 6.13:1；深绿文字与浅绿色主视觉约 8.35:1；次要文字与卡片底色约 5.54:1。错误与警告继续保留克制的语义色，用于提示状态。

## 项目内素材

| 路径（相对于项目根目录） | 用途 |
|---|---|
| web/src/assets/imagery/community-meal.png | 登录页和角色首页的社区就餐场景 |
| web/src/assets/imagery/steamed-chicken.png | 清蒸鸡肉的示意图 |
| web/src/assets/imagery/vegetable-tofu.png | 时蔬豆腐的示意图 |
| web/src/assets/imagery/mixed-grain-rice.png | 杂粮饭的示意图 |
| web/src/assets/imagery/meal-table.png | 其他缺图餐品的餐食搭配示意图 |

图片通过构建资源导入，部署时随前端发布，不依赖外部图片网站。首页场景图优先加载，菜品图使用懒加载。实际上传的菜品图片优先，通过原私有附件接口鉴权获取；无上传、读取失败或解码失败时，展示明确标注的示意图。图片不参与价格、过敏原或膳食判断。

## 最终生成提示词

社区场景图的完整提示词：

```text
Use case: photorealistic-natural. Asset type: landscape editorial hero photograph for a Chinese community elderly meal service website, 1536x1024 landscape composition. A warm candid lunchtime scene in a modest beautiful neighborhood dining room in China: a smiling Chinese elderly woman with short silver hair and her elderly husband seated at a pale wooden table, a friendly Chinese community kitchen staff member in a muted forest-green apron gently setting down a ceramic plate of freshly cooked lunch. Natural respectful expressions, realistic anatomy and hands, healthy ordinary older adults, no medical framing. White rice, steamed chicken and fresh green vegetables in simple ceramic bowls; natural portion sizes. Soft window daylight, warm cream walls, pale wood, subtle living greenery, muted forest green details, authentic documentary food and lifestyle photography, fine natural texture. Medium-wide eye-level composition, subjects and meal concentrated in central/right two thirds, pleasing uncluttered background, image must also crop well as a rectangular homepage feature. No text, no logos, no watermarks, no borders, no collage, no interface.
```

四张餐食图片分别调用内置工具生成。每个完整提示词由下列公共前缀、对应主体和公共后缀依次拼接。

公共前缀：

```text
Use case: photorealistic-natural. Asset type: landscape food photograph for a Chinese community meal website, 1024x768 or similar landscape.
```

对应主体：

```text
steamed-chicken:
A single shallow cream ceramic plate of Chinese steamed chicken, pale tender boneless chicken slices with a few thin ginger slices and a small green garnish. No chili, no peanuts, no rice side dishes.

vegetable-tofu:
A cream ceramic bowl of Chinese tofu and fresh green leafy vegetables, clearly visible pale tofu cubes and tender bok choy, light clear broth, a modest simple home-style dish. No meat, no chili, no peanuts.

mixed-grain-rice:
A small cream ceramic bowl of cooked Chinese mixed-grain rice, white rice and golden millet clearly visible, light fluffy grains. No side dishes, no meat, no chili.

meal-table:
An ordinary Chinese community lunch setting: small bowls of rice, lightly steamed chicken, tofu with green vegetables and a simple clear soup arranged together. Friendly balanced everyday lunch, modest portion sizes, no people.
```

公共后缀：

```text
Natural editorial food photography, camera at a gentle 45-degree angle, subject centered with enough space for responsive cropping. Pale wooden tabletop, softly blurred sage-green and warm cream dining-room background, consistent simple ceramic tableware. Gentle daylight, soft shadows, realistic textures, restrained natural color and low saturation, clean appetizing but ordinary food. No text, no logo, no watermark, no borders, no collage, no interface. The food is illustrative, not a claim about a real restaurant.
```
