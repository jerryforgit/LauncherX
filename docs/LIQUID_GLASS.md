# LauncherX 液态玻璃（Liquid Glass）实现说明

> 这份文档描述 LauncherX 中“液态玻璃 / frosted glass”的完整实现，用来在其它项目中复刻或移植。
> 代码位置：
>
> - `lawnchair/src/com/jerry/launcherx/modern/folder/GlassBackdrop.kt` —— 壁纸背景、模糊、AGSL 折射 shader
> - `lawnchair/src/com/jerry/launcherx/glass/GlassCardBackground.kt` —— 把玻璃画到任意圆角矩形卡片上
> - `lawnchair/src/com/jerry/launcherx/glass/GlassBackdropBridge.kt` —— 给 Java 代码用的门面
> - `lawnchair/src/com/jerry/launcherx/modern/folder/LargeFolderView.kt` —— 最早的使用者（大文件夹）
> - `src/com/android/launcher3/Hotseat.java` —— Dock 胶囊 / Dock 背景
>
> 适用 API：Android 13（API 33）及以上使用 `RuntimeShader` 做真实折射；低版本或 shader 失败时退化为“模糊壁纸位图直接贴上去”。

---

## 1. 视觉目标

不是“半透明 + 模糊”那么简单，而是模拟一块**边缘有厚度的玻璃板**：

1. 透过玻璃看到的是**模糊后的壁纸**，并且位置与屏幕上的壁纸对齐（含左右视差）。
2. 靠近边缘处，玻璃像**凸透镜**一样把背景向内“掰弯”（折射）。
3. 边缘有**色散**（红蓝分离的彩边），强度随边缘法线变化。
4. 有**菲涅尔反射**、方向性高光、内缘亮线（rim light）、底部暗边，形成立体感。
5. 可选一条**沿对角线扫过的高光带**（specular sweep），让玻璃“活”起来。
6. 深夜模式自动加深着色（否则白字在浅色玻璃上不可读）。

---

## 2. 渲染分层（从下到上）

`GlassCardBackground.drawSurface()` 的实际顺序：

```
1. 玻璃底：壁纸切片（模糊 ± 折射）         ← GlassBackdrop.draw()
2. 磨砂面纱（frosted 模式）：白色/黑色线性渐变   ← 上层薄雾
3. 噪点（noise shader, 3% alpha）          ← 打散色带，模拟磨砂颗粒
4. 1dp 描边                                ← 玻璃边缘
   · frosted 模式：固定 10% 白
   · liquid 模式：从左上到右下由 30% 白渐隐到 5% 白
```

纯色回退（没开玻璃）时：圆角矩形填 `cardColor × cardAlpha`，加 20% 白 1dp 描边。

---

## 3. 关键组件

### 3.1 `GlassBackdrop`（单例）

职责：

- 读取壁纸（`WallpaperManager.getDrawable()`），**只解码一次**，缩放后缓存（`sourceWallpaper`，最长边由质量等级决定：2048 / 3072 / 4096 px）。
- 按“模糊半径 + 采样率”生成并缓存**缩小版模糊壁纸**（`LinkedHashMap`，最多 8 个，半径按 4dp 量化）。
- 在**单线程后台 executor** 上构建，主线程只读缓存；构建完成后通过 listener 通知所有玻璃表面重绘。
- 通过 `generation` 计数器做失效：换壁纸（`ACTION_WALLPAPER_CHANGED`）、换质量、旋转都 `generation++`，旧的位图不会被复用。
- 记录壁纸水平偏移 `wallpaperOffset`（workspace 滚动时更新），绘制时把源矩形横向平移，实现**视差**。
- `draw()`：把“屏幕上某块矩形 → 壁纸位图对应切片”贴回去；`refraction > 0` 且 API ≥ 33 时走 AGSL 折射。

采样率：

| 用途 | 采样（相对屏幕） | 说明 |
|---|---|---|
| frosted 卡片 | 1/6 | 省内存，柔和的磨砂足够 |
| frost 全屏（弹出文件夹） | 1/2 | 避免拉伸后块状 |
| liquid（lens） | 按质量等级 1/2 … 1/1 | 折射需要更高的清晰度 |

质量等级（`liquidGlassQuality`）：

| 等级 | sample | 源图最长边 | 预模糊 |
|---|---|---|---|
| saver | 2 | 2048 | 1.5dp |
| balanced | 1 | 2048 | 0 |
| high | 1 | 3072 | 0 |
| max（默认） | 1 | 4096 | 0 |

性能参考（2944×1840 平板）：解码+缩放壁纸 ≈ 2.8s（只在首次/换壁纸），模糊 ≈ 6ms，绘制 ≈ 2ms。

### 3.2 `GlassCardBackground`

一个可在 `View.onDraw()` 里调用的“玻璃卡片”绘制器，参数：

| 属性 | 含义 | 默认 |
|---|---|---|
| `cardColor` / `cardAlpha` | 纯色回退颜色 | 由调用方给 |
| `surfaceAlpha` | 整体淡出（例如抽屉打开时让 Dock 胶囊变淡） | 1 |
| `cornerRadius` | 圆角 px | 0 |
| `refractionStrength` | 折射强度 | 0.75 |
| `dispersion` | 色散强度 | 1 |
| `lensBlurDp` | shader 内高斯采样半径（dp） | 2.4 |
| `blurRadiusDp` | 壁纸模糊半径（frosted 用），≤0 用模式默认 | -1 |
| `setGlassMode(frosted, liquid)` | 两个模式互斥/可切换 | 都关 |
| `setAnimateSpecular(bool)` | 高光带是否循环扫动 | false |

绘制时先 `canvas.clipPath(圆角矩形)`，再贴玻璃底、薄雾、噪点，最后描边。
如果背景位图还没准备好，**应回退到纯色卡片**，避免出现“看不见的玻璃”。

### 3.3 与 Compose 的关系

玻璃是**传统 Canvas 绘制**，不依赖 Compose。Compose 界面想用玻璃，需要在 `AndroidView`/自定义 View 的 `onDraw` 里调 `GlassCardBackground`，或者把 `GlassBackdrop.getLens()` 的位图作为 Compose `Image` 的背景再叠加渐变/描边。

---

## 4. 折射 Shader（AGSL，API 33+）

输入：一张模糊过的壁纸位图 `BitmapShader`（`uniform shader backdrop`）。
输出：该矩形区域内“隔着玻璃看到的世界”。

### uniforms

```glsl
uniform shader backdrop;   // 模糊壁纸，局部矩阵已缩放到 rect 尺寸
uniform float2 uSize;      // 卡片宽高（px）
uniform float uRadius;     // 圆角（px）
uniform float uRefraction; // 0..2
uniform float uDispersion; // 0..3
uniform float uBlurPx;     // 额外模糊半径（px），默认 2.2 * density
uniform float uTint;       // 着色强度（亮 0.14 / 暗 0.55）
uniform float uDark;       // 0/1
uniform float uSpecular;   // <0 = 不扫光；0..1 = 高光带位置
uniform float uDensity;    // density，用来让像素常量随屏幕缩放
```

### 算法步骤

1. **圆角矩形 SDF**：`roundedBox(p, b, r)`，得到到边缘的有符号距离 `d`；`d > 1.5` 直接返回背景（防止边缘外采样）。
2. **边缘法线**：用 SDF 的中心差分求 2D 法线 `n2`。
3. **斜面 (bevel)**：`bevel = min(min(b.x,b.y)*0.48, 19dp)`；`edge = clamp(1 + d/bevel, 0, 1)`，越靠近边缘越接近 1。
4. **法线合成**：`normalXY = n2 * pow(edge,1.65)*0.94 + faceNormal*0.045*(1-edge)`，再加 z 分量归一化 —— 即“边缘是斜面，中间是平面”。
5. **折射向量**（Snell，n=1.45）：
   `eta=1/1.45; cosi=normal.z; k=1-eta²(1-cosi²); t=eta*cosi-sqrt(k)`
   `rayXY = t*normalXY; rayZ = -eta + t*normal.z`
6. **偏移量**：`thickness = min((18 + 57*uRefraction)*density, min(b.x,b.y)*1.1)`
   `offset = rayXY / max(-rayZ, 0.05) * thickness * uRefraction`，再减去一点点向内收缩 `p*0.016*uRefraction`。
   → 效果：边缘附近采样点被“掰向”卡片内部，形成凸透镜的厚边。
7. **9 点模糊**：对 `coord + offset` 做 3×3 加权采样（中心 0.24，正交 0.12，对角 0.07），半径 `uBlurPx`。
8. **色散**：`split = n2 * pow(edge,2.8) * uDispersion * 2.6 * density`
   R 通道加上 `sample(+split).r - sample(0).r`，B 通道加 `sample(-split).b - sample(0).b`，各 ×0.8。
9. **着色**：`c = mix(c, tint, uTint + uDark*0.04)`，tint 亮色 `(0.97,0.985,1)`，暗色 `(0.10,0.15,0.22)`。
10. **光照**：
    - 方向光 `light=(-0.38,-0.93)`，`directional=pow(max(dot(n2,light),0),7)`
    - 反方向 `opposite`（×0.52）
    - 菲涅尔 `fresnel = 0.04 + 0.96*(1-normal.z)^5`
    - 内缘轮廓 `outline = 1 - smoothstep(0.4, 2.0, abs(d+0.8))`
    - `spec = edgeLight*0.075 + outline*(0.018 + directional*0.35 + opposite*0.15) + fresnel*0.035`
    - 环境反射：用偏移后的背景色 `environment`，`c = mix(c, environment, fresnel*0.12)`，再叠加 `mix(white, environment,0.18)*spec`
11. **焦散高光**：`caustic = exp(-((d + 3*density)/(1.65*density))²)`，乘 `opposite*0.038`。
12. **面内柔光**：沿对角线 `broad = exp(-((x*0.55+y*0.8+0.28)*1.6)²)`，混入环境色 5.5%。
13. **底部暗边**：`c -= pow(edge,8) * max(dot(n2,(0.45,-0.89)),0) * 0.085`。
14. **扫光带**（可选）：`travel=(x+y*0.35)/(w+h*0.35)`，`band=exp(-((travel-(-0.25+uSpecular*1.5))/0.10)²)`，加 `band*(0.05+edge*0.12)`。

### 完整 shader

```glsl
uniform shader backdrop;
uniform float2 uSize;
uniform float uRadius;
uniform float uRefraction;
uniform float uDispersion;
uniform float uBlurPx;
uniform float uTint;
uniform float uDark;
uniform float uSpecular;
uniform float uDensity;

float roundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 sampleScene(float2 px) {
    return backdrop.eval(clamp(px, float2(0.5, 0.5), uSize - float2(0.5, 0.5)));
}

half4 blurred(float2 px, float blur) {
    half4 c = sampleScene(px) * 0.24;
    c += sampleScene(px + float2(blur, 0.0)) * 0.12;
    c += sampleScene(px - float2(blur, 0.0)) * 0.12;
    c += sampleScene(px + float2(0.0, blur)) * 0.12;
    c += sampleScene(px - float2(0.0, blur)) * 0.12;
    c += sampleScene(px + float2(blur, blur) * 0.707) * 0.07;
    c += sampleScene(px - float2(blur, blur) * 0.707) * 0.07;
    c += sampleScene(px + float2(blur, -blur) * 0.707) * 0.07;
    c += sampleScene(px + float2(-blur, blur) * 0.707) * 0.07;
    return c;
}

half4 main(float2 coord) {
    float2 b = uSize * 0.5;
    float2 p = coord - b;
    float r = min(uRadius, min(b.x, b.y));
    float d = roundedBox(p, b, r);
    float3 base = float3(sampleScene(coord).rgb);
    if (d > 1.5) {
        return half4(half3(base), 1.0);
    }
    float e = 0.7;
    float2 n2 = normalize(float2(
        roundedBox(p + float2(e, 0.0), b, r) - roundedBox(p - float2(e, 0.0), b, r),
        roundedBox(p + float2(0.0, e), b, r) - roundedBox(p - float2(0.0, e), b, r)
    ) + float2(0.00001, 0.00001));
    float bevel = min(min(b.x, b.y) * 0.48, 19.0 * uDensity);
    float edge = clamp(1.0 + d / bevel, 0.0, 1.0);
    float slope = pow(edge, 1.65) * 0.94;
    float2 faceNormal = clamp(p / max(b, float2(1.0, 1.0)), float2(-1.0), float2(1.0))
        * 0.045 * (1.0 - edge);
    float2 normalXY = n2 * slope + faceNormal;
    float3 normal = float3(normalXY, sqrt(max(1.0 - dot(normalXY, normalXY), 0.001)));

    float eta = 1.0 / 1.45;
    float cosi = clamp(normal.z, 0.0, 1.0);
    float k = 1.0 - eta * eta * (1.0 - cosi * cosi);
    float tcoef = eta * cosi - sqrt(max(k, 0.0));
    float2 rayXY = tcoef * normalXY;
    float rayZ = -eta + tcoef * normal.z;
    float thickness = min((18.0 + uRefraction * 57.0) * uDensity, min(b.x, b.y) * 1.1);
    float2 offset = rayXY / max(-rayZ, 0.05) * thickness * uRefraction;
    offset -= p * 0.016 * uRefraction;

    float3 c = float3(blurred(coord + offset, uBlurPx).rgb);
    float2 split = n2 * pow(edge, 2.8) * uDispersion * 2.6 * uDensity;
    c.r += (float(sampleScene(coord + offset + split).r)
        - float(sampleScene(coord + offset).r)) * 0.8;
    c.b += (float(sampleScene(coord + offset - split).b)
        - float(sampleScene(coord + offset).b)) * 0.8;

    float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(float3(luma, luma, luma), c, 1.035);
    float3 tint = mix(float3(0.97, 0.985, 1.0), float3(0.10, 0.15, 0.22), uDark);
    c = mix(c, tint, uTint + uDark * 0.04);

    float2 light = normalize(float2(-0.38, -0.93));
    float directional = pow(max(dot(n2, light), 0.0), 7.0);
    float opposite = pow(max(dot(n2, -light), 0.0), 7.0) * 0.52;
    float fresnel = 0.04 + 0.96 * pow(1.0 - normal.z, 5.0);
    float outline = 1.0 - smoothstep(0.4, 2.0, abs(d + 0.8));
    float edgeLight = (directional + opposite) * pow(edge, 4.0);
    float spec = edgeLight * 0.075
        + outline * (0.018 + directional * 0.35 + opposite * 0.15);
    spec += fresnel * 0.035;
    float3 environment = float3(sampleScene(coord + n2 * 34.0 * uDensity).rgb);
    c = mix(c, environment, fresnel * 0.12);
    c += mix(float3(1.0), environment, 0.18) * spec;

    float caustic = exp(-pow((d + 3.0 * uDensity) / (1.65 * uDensity), 2.0));
    c += caustic * opposite * 0.038;

    float2 face = p / max(b, float2(1.0, 1.0));
    float broad = exp(-pow((face.x * 0.55 + face.y * 0.8 + 0.28) * 1.6, 2.0));
    float3 ambient = float3(sampleScene(float2(b.x * 0.4, b.y * 0.45)).rgb);
    c = mix(c, clamp(ambient + float3(0.15), float3(0.0), float3(1.0)), broad * 0.055);
    c -= pow(edge, 8.0) * max(dot(n2, float2(0.45, -0.89)), 0.0) * 0.085;

    if (uSpecular >= 0.0) {
        float travel = (coord.x + coord.y * 0.35) / max(uSize.x + uSize.y * 0.35, 1.0);
        float band = exp(-pow((travel - (-0.25 + uSpecular * 1.5)) / 0.10, 2.0));
        c += band * (0.05 + edge * 0.12);
    }
    return half4(half3(c), 1.0);
}
```

### Kotlin 侧调用（API 33+）

```kotlin
val shader = RuntimeShader(LENS_SHADER)
shader.setFloatUniform("uSize", w.toFloat(), h.toFloat())
shader.setFloatUniform("uRadius", radiusPx)
shader.setFloatUniform("uRefraction", 0.75f)
shader.setFloatUniform("uDispersion", 1f)
shader.setFloatUniform("uBlurPx", 2.2f * density)
shader.setFloatUniform("uTint", if (dark) 0.55f else 0.14f)
shader.setFloatUniform("uDark", if (dark) 1f else 0f)
shader.setFloatUniform("uSpecular", phase /* -1 = 关闭 */)
shader.setFloatUniform("uDensity", density)

// 关键：背景不在 View 树里，只能作为 BitmapShader 输入
val input = BitmapShader(blurredWallpaper, CLAMP, CLAMP).apply {
    setLocalMatrix(Matrix().apply {
        setScale(w / srcRect.width(), h / srcRect.height())
        postTranslate(-srcRect.left * scaleX, -srcRect.top * scaleY)
    })
}
shader.setInputShader("backdrop", input)

canvas.withSave {
    translate(left, top)
    drawRect(0f, 0f, w, h, Paint().apply { this.shader = shader })
}
```

---

## 5. 壁纸对齐与视差

`sourceRectFor()` 把 View 的屏幕坐标映射到（缩小后的）壁纸位图：

```kotlin
val scale = bitmap.height / displayMetrics.heightPixels
val parallaxPx = (bitmap.width - displayWidth * scale) * wallpaperOffset
srcRect.set(
    (screenX + left) * scale + parallaxPx,
    (screenY + top) * scale,
    (screenX + right) * scale + parallaxPx,
    (screenY + bottom) * scale
)
```

- `wallpaperOffset` 由 workspace 滚动时写入（0..1），让玻璃里的壁纸跟着桌面一起横移。
- 位图只模糊一次，靠 `srcRect` 取切片，所以滚动时不需要重建（`refreshSurfaces()` 只负责让 View 重绘）。

---

## 6. 失效与生命周期

| 事件 | 处理 |
|---|---|
| 壁纸变化 | `ACTION_WALLPAPER_CHANGED` → `generation++`，清空缓存 |
| 旋转 / 分辨率变化 | `ComponentCallbacks.onConfigurationChanged` → `generation++` |
| 质量等级变化 | `invalidateQuality()` → 清空 lens 缓存 |
| 系统内存紧张 | `onLowMemory()` 仅清缓存（不动源图） |
| View 回收 | `GlassCardBackground.release()` 注销 listener / 取消动画 |

`get()` / `getLens()` 是**非阻塞**的：第一次返回 null，后台线程建好后通过 `addListener` 回调，调用方重绘。

---

## 7. 常见坑（移植时最容易踩）

1. **背景不在 View 树里**：不能对 View 用 `RenderEffect` 做背景模糊，必须自己把壁纸位图作为 shader 输入。
2. **`WallpaperManager.getDrawable()` 可能返回黑色**：开机过早、权限不足或 ROM 限制时会拿到纯黑位图，缓存后就是“黑色玻璃”。构建时应检测采样亮度，全黑就别缓存。
3. **位图缓存要按半径量化**：连续滑杆会产生无限多个位图，必须量化（这里 4dp 一档，最多 8 个）。
4. **模糊要降采样**：1/6 屏幕分辨率足够，1/1 只给折射用；否则内存和耗时都会爆。
5. **API < 33 没有 `RuntimeShader`**：直接贴模糊位图，不要尝试用 `RenderScript`。
6. **深色模式必须加重着色**：壁纸通常很亮，白色文字会不可读（0.14 → 0.55）。
7. **`BitmapShader` 的 local matrix** 必须把源矩形缩放+平移到目标矩形，否则折射采样会错位。
8. **动画驱动重绘**：扫光带需要 `postInvalidateOnAnimation()` 自驱动，View 不可见时自然停止。
9. **别在主线程解码壁纸**：4K 壁纸解码约 2–3s，必须放后台线程。
10. **Compose**：`RuntimeShader` 也能用在 Compose 的 `Paint` 上，但要注意 Compose 的绘制坐标系与 `localMatrix`。

---

## 8. 参数推荐（直接可用的默认值）

```text
refractionStrength = 0.75      // 0 = 关；0.6~1.0 比较自然
dispersion         = 1.0       // 0 = 无色散；>2 会显得廉价
lensBlurDp         = 2.4       // 大面板可降到 1.5
frostedRadiusDp    = 28
liquidRadiusDp     = 20
cornerRadius       = 高度 / 2（胶囊） 或 24dp（卡片）
tint(light)        = 0.14
tint(dark)         = 0.55
specularDuration   = 3600ms     // 扫光一次
border             = 1dp, white 10%（frosted）/ 30%→5% 渐变（liquid）
noise              = 96×96 随机灰度，alpha 3%
```
