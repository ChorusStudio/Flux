# Flux

Flux 是面向 Minecraft Fabric 服务端的声明式渲染库。
它基于 Display 实体、箱子菜单与隐身 Bat，在每 Tick 的声明中描述画面、菜单与运镜；底层负责 diff，并将实体生命周期变更延迟到 `END_SERVER_TICK` 再执行。

`scene`、`gui`、`camera` 三层相互独立。模组加载时自动初始化，无需再次调用 `init()`。

Scene 部分衍生自 [FluxUI](https://github.com/wiyuka-owo/FluxUI)，已重写为 Fabric / Kotlin。本库不是 FluxUI 的 drop-in：不提供 `beginWindow`、世界空间射线点击，亦不依赖 Bukkit。

文档：[API](docs/api.md) · [架构说明](docs/architecture.md)

## 特性

* **即时渲染**：在 Tick 循环中声明画面，底层处理 Display 实体的生成、更新、复用与销毁。
* **按观众隔离**：场景按观众持有独立画布；菜单按玩家隔离；每台相机持有各自的任务。
* **可见性过滤**：Display 实体默认仅向指定观众发送 spawn / data 包。
* **插值动画**：节点可设置 `interpolationTicks`，使用原版 Display 的 Transformation Interpolation。
* **箱子 GUI**：声明式槽位与点击处理；状态变更后自动重绘。
* **任务式运镜**：以隐身 Bat 为机位，内置飞向、跟随注视、抛物线等任务。

## 环境要求

* **Minecraft**：1.21.11
* **Java**：21
* **依赖**：Fabric Loader、Fabric API、`fabric-language-kotlin`

## 集成

将仓库发布到 GitHub（`ChorusStudio/flux`）并打 tag 后，通过 [JitPack](https://jitpack.io) 引入。将 `<tag>` 替换为实际 git tag 或 commit SHA：

[![](https://jitpack.io/v/ChorusStudio/Flux.svg)](https://jitpack.io/#ChorusStudio/Flux)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    modImplementation("com.github.ChorusStudio:flux:<tag>")
    include("com.github.ChorusStudio:flux:<tag>")
}
```

`include` 将库嵌入模组 jar。JitPack 需解析 Loom 发布的 remap 产物；若依赖无法解析，请查看 JitPack 构建日志。亦可不使用 `include`，将 remap jar 放入 `mods`。

`fabric.mod.json`：

```json
"depends": {
  "flux": "*"
}
```

## 快速入门

### 1. 场景（Scene）

构造 `FluxScene` 即完成注册。每个仍在观看的玩家须在每 Tick 调用一次 `view`；连续未调用的观众将在下一帧销毁画布。不再使用时调用 `destroy()`。

坐标系：`+x` 向右、`+y` 向上、`+z` 面向观察者。`text` 按中心放置；`rect` / `itemDisplay` 以 `(x, y)` 为原点向 `+x/+y` 生长；`topLeftRect` 以 `(x, y)` 为左上角向 `-y` 生长。`FluxColor` 构造顺序为 `(a, r, g, b)`。

```kotlin
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
import top.mythcraft.flux.scene.FluxCanvas
import top.mythcraft.flux.scene.FluxColor
import top.mythcraft.flux.scene.FluxScene

class Overlay {
    private val scene = FluxScene("overlay")

    fun tick(viewers: Collection<ServerPlayer>) {
        for (player in viewers) {
            val look = player.lookAngle
            val center = player.eyePosition.add(look.scale(3.0))
            val orientation = FluxCanvas.screenOrientation(
                Vector3d(0.0, 1.0, 0.0),
                Vector3d(-look.x, -look.y, -look.z),
            )
            scene.view(player, center, orientation) {
                zStep(0.01f)
                text("title", "Hello") { x = 0.0; y = 1.0; scale = 0.4f }
                rect("bar", width = 2f, height = 0.2f, FluxColor(200, 80, 80, 80)) {
                    x = -1.0
                    y = 0.0
                }
                group("panel") {
                    transform { translate(0f, -0.5f, 0f) }
                    text("hint", "group + transform")
                }
            }
        }
    }

    fun destroy() = scene.destroy()
}
```

`screenOrientation(yAxis, zAxis)` 的 `zAxis` 为屏幕法线（朝向观察者）。上例将画面放在视线前方并朝向玩家。世界对齐使用 `FluxCanvas.STANDARD_ORIENTATION`（请勿修改该常量）。禁止在 `content` 中直接 spawn / discard 或修改 transformation。水平网格见 `FluxGrid` / `FluxFade`。

### 2. 箱子菜单（GUI）

`content` 每一帧都会再次执行。状态须放入 `remember` / `mutableStateOf` / `stateMapOf`（按调用位置记忆，顺序须保持稳定）。写入状态会自动 `requestRender()`。

```kotlin
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import top.mythcraft.flux.gui.FluxGui

class SettingsGui(private val player: ServerPlayer) {
    private val gui = FluxGui(player, Component.literal("Settings"), rows = 3) {
        val enabled = mutableStateOf(true)
        pattern(
            "         ",
            "    B    ",
            "    I    ",
        ) {
            bind('B') {
                button(ItemStack(Items.LIME_DYE)) {
                    left { enabled.value = !enabled.value }
                }
            }
            bind('I') {
                item(ItemStack(if (enabled.value) Items.GREEN_WOOL else Items.RED_WOOL))
            }
        }
    }

    fun open() = gui.open()
}
```

`open()` 幂等。玩家关闭菜单时 `onClose` 触发一次。静态菜单仅在脏标记时渲染；`ticking = true` 时每 Tick 重渲染。

### 3. 运镜（Camera）

构造时仅创建隐身 Bat，不进入世界；spawn / discard 延迟到 `END_SERVER_TICK`。任务在服务端线程推进位姿，须在每 Tick 调用 `camera.tick()`。

```kotlin
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import top.mythcraft.flux.camera.CameraPose
import top.mythcraft.flux.camera.CinematicCamera
import top.mythcraft.flux.camera.task.FlyToTask

class Flyover(private val player: ServerPlayer) {
    private var camera: CinematicCamera? = null

    fun start(hoverPos: Vec3, yaw: Float, pitch: Float) {
        camera = CinematicCamera(player.level(), CameraPose.of(player)).apply {
            onSpawned { c ->
                player.setCamera(c.entity)
                c.execute(FlyToTask(hoverPos, targetYaw = yaw, targetPitch = pitch, durationTicks = 40))
            }
        }
    }

    fun tick() {
        camera?.tick()
    }

    fun stop() {
        if (!player.hasDisconnected()) {
            player.setCamera(null)
        }
        camera?.destroy()
        camera = null
    }
}
```

结束运镜时须 `setCamera(null)`，将视角切回玩家自身。默认实体 tag 为 `flux_camera_bat`。

节点、槽位与运镜任务的参数见 [API](docs/api.md)。`view` / `camera.tick()` 的调用约定，以及实体加入世界的时机，见 [架构说明](docs/architecture.md)。

## 注意事项

1. **组件 / 节点 ID**：场景依赖 id 字符串追踪并复用 Display。同一 `group` 下 id 必须唯一。动态文本应使用固定 id（例如 `text("txt_count", "次数: $n")`），不得将变化的内容作为 id。
2. **必须 destroy**：玩家离开、维度卸载或模组停用时调用 `FluxScene.destroy()` / `CinematicCamera.destroy()`，否则实体会残留。菜单在玩家关闭时自动触发 `onClose`。
3. **view 与 tick**：场景须对每个仍在观看的观众每 Tick 调用 `view`；相机须每 Tick 调用 `camera.tick()`。未调用则分别视为观众离开、任务停止推进。
4. **remember 顺序**：`remember` / `mutableStateOf` 按调用位置记忆，每帧顺序必须稳定。
5. **Z-Fighting**：底层已通过 `zStep` 提供微小偏移，复杂共面内容仍应自行控制 z。
6. **禁止在声明块中修改实体**：场景 `content` 只负责声明。相机的 spawn / discard 须经框架延迟队列。
7. **自定义相机 tag**：`BatCameraMixin` 仅对默认 tag `flux_camera_bat` 取消蝙蝠 AI。传入自定义 tag 时须自行禁用该实体的 AI，否则运镜会被原版行为影响。
8. **还原视角**：`CinematicCamera.destroy()` 会先使乘客离乘再 `discard()`，但不恢复玩家视角。结束运镜时须调用 `player.setCamera(null)`。

## 鸣谢

+ [FluxUI](https://github.com/wiyuka-owo/FluxUI)（MIT，Copyright 2026 wiyuka）：本库 Scene 部分衍生自该项目，实现已重写。完整许可见 `NOTICE`。
+ [TheCymaera](https://github.com/TheCymaera/minecraft-hologram) 提供使用文本展示实体渲染三角形的方案。

## License

Apache License 2.0。衍生自 FluxUI 的部分另见 `NOTICE` 中的 MIT 声明。
