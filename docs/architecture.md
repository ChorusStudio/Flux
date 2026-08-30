# 架构说明

`scene`、`gui`、`camera` 三层相互独立，可单独使用。

| | 使用时 | 框架 |
| --- | --- | --- |
| **scene** | 对每个观众调用 `view`，在 lambda 中声明节点 | 与上一帧 diff，仅更新变化的 Display；spawn / discard 延迟到 `END_SERVER_TICK` |
| **gui** | 在 `content` 中声明槽位与状态 | 仅重写变化的槽位；状态写入后自动重绘 |
| **camera** | 每 Tick 调用 `camera.tick()` | Bat 的 spawn / discard 延迟到 `END_SERVER_TICK` |

模组加载时自动初始化，无需再次调用 `init()`。

## 场景

`view` 的 lambda 仅收集节点状态，不修改实体。本 Tick 未对某玩家调用 `view` 时，其画布将在下一帧销毁。玩家断线时同样销毁。

每个 Display 仅向对应观众发送 spawn / data 包。

`screenCenter` 相对实体位置漂移超过约 120 格时，框架传送该实体，而不销毁整个实体池。共面内容可使用 `zStep`；复杂重叠仍须自行错开 `z`。

场景连续渲染失败 3 次后将自动销毁，异常不向上传播。

## 菜单

`content` 每一帧都会再次执行，因此计数器与开关不得保存在普通局部变量中。`mutableStateOf` 按调用顺序复用上一帧的对象；顺序变化将导致状态错位。

默认仅在状态变化时重绘。需要每 Tick 刷新的界面应设置 `ticking = true`。

输入槽的内容由玩家控制，重绘不会覆盖。Shift 快速移动与拖放已禁用。

## 运镜

构造 `CinematicCamera` 时实体尚未加入世界。`setCamera` 与 `execute` 应在 `onSpawned` 中调用。若在 spawn 刷新之前调用 `destroy()`，实体不会进入世界。

任务须在服务端线程中调用 `camera.tick()` 推进。未调用则任务停止。

`destroy()` 会先使乘客离乘再移除实体，但不恢复玩家视角。结束时须调用 `player.setCamera(null)`。

## 约束

- 禁止在场景 `content` 中 spawn / discard / 调用 `setTransformation`。
- 禁止直接 spawn / discard 相机实体。
- 不得将变化的字符串作为节点 id。
- `remember` 的调用顺序每帧必须稳定。
- 自定义相机 tag 不会触发 `BatCameraMixin`，须自行禁用该实体的 AI。
