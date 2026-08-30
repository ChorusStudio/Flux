# API

接入步骤与完整示例见 [README](../README.md)。本页列出公共调用。请勿直接使用 `scene/backend`、`gui/backend` 与 mixin。

## 场景

包：`top.mythcraft.flux.scene`

对每个仍在观看的玩家，须于每 Tick 调用一次 `view`。连续未调用的观众将在下一帧销毁画布。不再使用时调用 `destroy()`。

| 方法 | 说明 |
| --- | --- |
| `FluxScene(sceneId)` | 构造即注册。`sceneId` 仅用于日志 |
| `view(player, screenCenter, orientation) { … }` | 声明该玩家本 Tick 的画面 |
| `removeViewer(player)` | 立即销毁该玩家的画布 |
| `destroy()` | 注销并释放全部实体（幂等） |

`content` 仅声明节点，禁止在其中 spawn / discard 或修改 Display。

### 朝向与坐标

`screenCenter` 为画布锚点（实体放置位置）。`orientation` 决定屏幕朝向：

- 面向玩家：`FluxCanvas.screenOrientation(上方向, 法线)`。法线朝向观察者。将画面置于视线前方的写法见 README（`zAxis = -look`）。
- 与世界对齐：`FluxCanvas.STANDARD_ORIENTATION`。请勿修改该常量。

画布局部坐标：`+x` 向右，`+y` 向上，`+z` 面向观察者。

| 节点 | 原点 |
| --- | --- |
| `text` | 中心 |
| `rect` / `itemDisplay` | `(x, y)`，向 `+x/+y` 生长 |
| `topLeftRect` | `(x, y)` 为左上角，向 `-y` 生长 |

`FluxColor(a, r, g, b)`，各通道范围为 `0..255`。构造顺序为 ARGB，而非 RGBA。

### 节点

同一画布内 id 必须稳定且唯一。循环中使用 `"item_$index"`。动态文本应使用固定 id，例如 `text("count", "次数: $n")`；若将变化的内容作为 id，框架会将其视为新实体。

`group("wall") { text("seg") }` 的实际 id 为 `wall/seg`。`group` 同时隔离矩阵栈。

```kotlin
zStep(0.01f)
group("panel") {
    transform { translate(0f, -0.5f, 0f) }
    text("title", "Hello") { x = 0.0; y = 1.0; scale = 0.4f }
    rect("bar", 2f, 0.2f, FluxColor(200, 80, 80, 80)) { x = -1.0 }
}
```

`transform` 内可调用 `translate`、`rotateX` / `rotateY` / `rotateZ`、`scale`、`skew`，旋转与切变单位为度。`zStep` 使后续节点的 z 自动累加，用于减轻共面 z-fighting。

| 调用 | 说明 |
| --- | --- |
| `text(id, text) { … }` | 默认 `scale = 0.45`、`opacity = 255`、`align = CENTER` |
| `rect(id, width, height, color) { … }` | 纯色矩形 |
| `topLeftRect(id, x, y, width, height, color) { … }` | 以左上角定位 |
| `itemDisplay(id, width, height, color) { … }` | 染色玻璃板，模型为 `flux:ui_plane_<color>_<alpha>` |
| `triangle(id, p1, p2, p3, color) { … }` | 由三块 TextDisplay 拼合 |

节点配置块中还可设置 `z`、`billboard`、`seeThrough`、`interpolationTicks`（`0` 表示瞬间到位）。文本对齐为 `FluxNode.Align.LEFT` / `CENTER` / `RIGHT`。

水平网格：`FluxGrid.draw(canvas, player, anchor, yLevel, color, idPrefix) { size, color -> rect("cell", size, size, color) }`。按距离计算 alpha 可使用 `FluxFade.calculateFadeAlpha`。

## 菜单

包：`top.mythcraft.flux.gui`

`content` 每一帧都会再次执行。可变状态须放入 `mutableStateOf` / `stateMapOf` / `remember`，不得保存在普通局部变量中。

| 方法 | 说明 |
| --- | --- |
| `FluxGui(player, title, rows = 6, ticking = false) { … }` | `rows` 为 `1..6`。需每 Tick 刷新时设置 `ticking = true` |
| `open()` | 打开菜单（幂等） |
| `onOpen { }` / `onClose { }` | `onClose` 在菜单被移除时触发一次 |
| `requestRender()` | 写入 `mutableStateOf` 时会自动调用 |
| `close()` | 关闭容器 |

静态菜单仅在脏标记时重绘。

### 布局

`pattern` 按字符网格映射到槽位，空格表示空槽：

```kotlin
pattern(
    "         ",
    "    B    ",
) {
    bind('B') {
        button(ItemStack(Items.LIME_DYE)) {
            left { enabled.value = !enabled.value }
        }
    }
}
```

亦可使用 `at(x, y)` / `at(slot)` 绝对定位，以及 `space` / `nextLine` 移动光标。

| 组件 | 说明 |
| --- | --- |
| `item(stack)` | 仅展示 |
| `button(stack) { left / right / shiftLeft / shiftRight / any }` | 可点击。`stepper(step, shiftStep) { delta -> }` 将左键 / 右键映射为有符号增量 |
| `slot()` / `inputSlot()` | 输入槽；`inputSlot()` 返回当前内容 |
| `slotStack(i)` | 读取指定槽位 |
| `exceptSlot(…)` / `clearExcept()` | 将槽位排除出交互 |

### 状态

`remember` / `mutableStateOf` 按调用顺序记忆。每帧顺序必须稳定；在条件分支中增减调用会导致状态错位。

```kotlin
val enabled = mutableStateOf(true)
val counts = stateMapOf(mapOf("a" to 0))
```

写入 `enabled.value` 或修改 `counts` 将触发重绘。

## 运镜

包：`top.mythcraft.flux.camera`

构造时仅创建隐身 Bat，不进入世界。spawn / discard 延迟到 `END_SERVER_TICK`。须每 Tick 调用 `camera.tick()` 以推进任务。

| 方法 | 说明 |
| --- | --- |
| `CinematicCamera(level, pose, tag = CAMERA_BAT_TAG)` | 可用 `CameraPose.of(player)` 从实体读取位姿 |
| `onSpawned { }` | 实体加入世界后执行。应在此调用 `player.setCamera(c.entity)` 与 `execute(task)` |
| `execute(task)` | 中断当前任务并启动新任务 |
| `tick()` | 推进当前任务 |
| `destroy()` | 使乘客离乘后 `discard`。不恢复玩家视角 |
| `entity` | 底层 Bat。任务中可修改位姿与速度，请勿直接 spawn / discard |

结束运镜时先调用 `player.setCamera(null)`，再调用 `destroy()`。若玩家已断线，则跳过 `setCamera`。

默认 tag 为 `flux_camera_bat`，库内 mixin 将取消该实体的 AI。使用其它 tag 时须自行禁用 AI。

### 任务

`CameraTask.tick` 返回 `true` 表示完成，随后调用 `onFinish`。被 `execute` 替换或相机销毁时调用 `onInterrupt`。二者互斥。`task.withCallback { }` 在 `onFinish` 之后追加回调。

| 任务 | 说明 |
| --- | --- |
| `FlyToTask(targetPos, durationTicks, targetYaw?, targetPitch?, lookAtTarget?, maxSpeed, easing)` | 按缓动曲线飞向目标。默认 `Easing.OUT_QUINT`、`maxSpeed = 15` |
| `FollowAndLookTask(positionProvider, lookAtProvider)` | 持续跟随并注视，直至被中断 |
| `ParabolaFlyTask(targetPos, durationTicks, arcHeight = 5.0)` | 二次贝塞尔抛物线 |

自定义任务须实现 `CameraTask`，在 `tick` 中更新 `camera.entity` 的位置、速度或朝向。可使用 `CameraMath.lookRotation`、`lerpAngleDegrees`、`velocityToward`，以及 `Easing.LINEAR` / `OUT_QUINT` / `IN_OUT_QUINT`。
