# 神化饰品宝石修复 (apoth-curio-socket-fix)

让 **神化 (Apotheosis)** 镶在 **Curios 饰品栏物品**（戒指 / 项链 / 护符 / 书 …）上的宝石，
**属性加成正常生效**。

- Minecraft 1.20.1 / Forge 47.2.20
- 目标版本：Apotheosis 7.4.8、Curios 5.14.1（其它 7.x / 5.x 大概率也可）
- 协议：MIT

---

## 修的是什么问题

Apotheosis 把宝石属性加进物品时走的是原版 `ItemAttributeModifierEvent`
（`AdventureEvents#affixModifiers`）：

```java
SocketHelper.getGems(stack).addModifiers(LootCategory.forItem(stack), e.getSlotType(), consumer);
```

而 `GemInstance#addModifiers(EquipmentSlot slot, ...)` 内部有一段**槽位过滤**：

```java
for (EquipmentSlot itemSlot : this.cat.getSlots()) {
    if (itemSlot != slot) continue;      // 宝石分类的槽位 != 当前查询槽位 → 一条都不加
    ...addModifiers...
}
```

戒指、项链、护符这类 Curios 物品，`LootCategory.forItem(stack)` 得到的是 **`NONE`**，
而 `NONE.getSlots()` 是**空数组** —— 这个循环一次都不会命中。

结果：宝石能镶上去、`GemInstance#isValid()` 也是 `true`，但**属性一条都加不上**。
放进原版头盔/胸甲/剑里则一切正常，因为那些物品的分类里带着 `HEAD` / `CHEST` / `MAINHAND`。

## 怎么修的

Curios 有自己的 `CurioAttributeModifierEvent`：饰品栏物品计算属性修饰符时触发。
本模组监听它，对每颗宝石**跳过上面那段槽位过滤**，直接调用过滤之后真正干活的那一步：

```java
gem.getBonus(cat, rarity).ifPresent(bonus ->
    bonus.addModifiers(gemStack, rarity, event::addModifier));
```

除了"槽位过滤"，所有判定（`LootCategory`、`LootRarity`、`isValid()` 的 bonus 存在性）
都沿用 Apotheosis 自己的逻辑，所以不会给本来该被过滤掉的宝石凭空加属性 ——
只有"没有原版槽位"的 Curios 物品受影响。

同一 UUID 的修饰符只会加一次（宝石的 UUID 写在宝石 NBT 里，重复计算会让属性翻倍）。

---

## 生效条件

| 装了 Curios | 装了 Apotheosis | 行为 |
|:---:|:---:|---|
| ✅ | ✅ | 饰品栏物品上的宝石属性生效 |
| ✅ | ❌ | 什么也不做（日志一行提示） |
| ❌ | ✅ | 什么也不做（日志一行提示） |
| ❌ | ❌ | 什么也不做，不会崩 |

`curios` 与 `apotheosis` 在 `mods.toml` 里都是 `mandatory=false`（软依赖）：
**不会**因为缺前置而拒绝加载，也不会提前加载任何前置的类（注册前用 `ModList.isLoaded` 守过）。

---

## 目前**没有**覆盖的部分

**只修了"属性加成"。** 宝石的触发式效果都还没有接：

- `doPostAttack` / `doPostHurt` / `onHurt`（命中、受击时的触发效果）
- `onArrowFired` / `onArrowImpact`（弓箭）
- `getDurabilityBonusPercentage`（耐久加成）
- `getDamageProtection` / `getDamageBonus` / `onShieldBlock` / `onBlockBreak` / `onItemUse`
- 宝石的附魔等级加成（`getEnchantmentLevels`）

也就是说：**属性类宝石（如 +攻击力、+生命上限、+暴击率）会生效；
纯触发型的宝石在饰品栏里仍然不会触发。** 这些要等后续版本。

---

## 安装

1. 装好 Minecraft 1.20.1 + Forge 47.2.20（或更高 47.x）
2. 把 `apothcuriosocketfix-1.0.0.jar` 放进 `mods/`
3. 同时需要 `Apotheosis` 与其前置 `Placebo`；要生效还需要 `Curios`

服务端 / 客户端都要装（这是纯服务端逻辑的修复，但 Curios 的饰品位本身是双端的）。

---

## 从源码构建

```bash
# 需要 JDK 17+ 与 ForgeGradle 6
./gradlew build
# 产物:build/libs/apothcuriosocketfix-1.0.0.jar
```

编译期依赖不打进 jar，需要把它们放进项目根目录的 `libs/`（该目录已被 `.gitignore` 忽略）：

```
libs/curios-forge-5.14.1+1.20.1.jar
libs/Apotheosis-1.20.1-7.4.8.jar
libs/Placebo-1.20.1-8.6.3.jar
```

`build.gradle` 用 `fileTree(dir: 'libs', include: ['*.jar'])` 收集，换版本只需换文件。

---

## 来源与致谢

**本模组最初来源于对下面两个项目的整合与修改**（与它们**没有依赖关系**，装不装都能独立运行）：

- **[Apothic Curios](https://github.com/Daripher/Apothic-Curios)**（作者 Daripher）
  —— 神化与 Curios 的兼容桥。它证明了「Curios 物品可以拥有神化的词缀与镶孔」，
  并给出了在 `CurioAttributeModifierEvent` 里补属性的做法；本模组的 `CurioGemAttributes`
  就是从它整合、改写而来的。
- **[Apotheosis x Iron's Spellbooks Compat](https://www.curseforge.com/minecraft/mc-mods/apotheosis-x-irons-spellbooks-compat)**
  —— 为铁魔法装备添加神化词缀与宝石的兼容模组。本模组处理 `LootCategory.NONE`、
  让宝石按分类分发到「非原版装备槽」物品上的做法来源于它。

上游项目：

- [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) —— 宝石与镶槽系统
- [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) —— 饰品栏与属性事件

**与 Apothic Curios 功能重合**：本模组只修「宝石属性」，覆盖范围更窄；
若同时安装，建议先确认是否出现属性重复计算。

本模组自身以 MIT 发布；上述项目的代码与素材版权归各自作者所有。

---

## 协议

[MIT](LICENSE) © 2026 hengxinlai7-netizen
