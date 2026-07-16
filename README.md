# NehaNPCs

Paper 1.21.x / Java 21 向けの、ZNPCs風NPC管理プラグインです。

`/npc` と `/znpcs` の両方で操作できます。通常のBukkit/Paper Entity NPCを中心に、作成、保存復元、クリックアクション、会話、パス移動、見た目編集まで実装しています。

ProtocolLibに依存しています。サーバーの `plugins` フォルダへProtocolLib.jarも配置してください。
Minecraft 1.21.9以降（1.21.11を含む）では、GameProfile対応済みのProtocolLib Development Buildが必要です。5.4.0ではPLAYER NPCを生成できません。Paper 1.21.11ではJava 25を使用してください。Java 26ではPaperのreflection remapperがProtocolLibの動的生成クラスを処理できません。

## Build

```sh
mvn package
```

生成物:

```txt
target/nehanpcs-0.1.0.jar
```

Gradle構成も入っていますが、この環境ではGradle本体が未導入だったため、検証はMavenで行っています。

## Dependencies

- Paper API `1.21.11-R0.1-SNAPSHOT`
- ProtocolLib `net.dmulloy2:ProtocolLib:5.4.0`（コンパイル用。1.21.11実行時はDevelopment Build）

## Basic

```txt
/npc create <id> <type> <name...>
/npc list
/npc teleport <id>
/npc move <id>
/npc delete <id>
/npc save
/npc reload
```

例:

```txt
/npc create 1 zombie &aGuide
/npc create 2 player Notch
/npc list
/znpcs teleport 1
```

## Player NPC

ProtocolLib packetでFake Playerを表示します。

```txt
/npc create <id> player <name>
/npc skin <id> <username>
/npc toggle <id> mirror
```

`skin` はMojang APIから署名付きtextures propertyを取得して保存します。`mirror` を有効にすると、見るプレイヤーごとに自分のGameProfileを使って表示します。

## Actions

NPCクリック時に複数アクションを実行できます。

```txt
/npc action add <id> <CMD|CONSOLE|CHAT|MESSAGE|SERVER> <content...>
/npc action list <id>
/npc action remove <id> <actionId>
/npc action cooldown <id> <actionId> <seconds>
```

例:

```txt
/npc action add 1 MESSAGE &aWelcome, %player%!
/npc action add 1 CONSOLE give %player% apple 5
/npc action cooldown 1 0 5
```

Placeholders:

```txt
%player%
%uuid%
%world%
%x%
%y%
%z%
%npc_id%
%npc_name%
```

`SERVER` は `server-transfer.channel` のPlugin Messaging Channelを使います。初期値は `BungeeCord` です。

## Appearance

```txt
/npc lines <id> <text...>
/npc height <id> <height>
/npc equip <id> <HAND|OFFHAND|HELMET|CHESTPLATE|LEGGINGS|BOOTS>
/npc type <id> <type>
/npc customize <id> <key> <value>
/npc toggle <id> <look|holo|glow|mirror|collision> [color]
```

例:

```txt
/npc lines 1 &aGuide|&7Click me
/npc height 1 2.3
/npc equip 1 HAND
/npc type 1 villager
/npc customize 1 setProfession FARMER
/npc toggle 1 look
/npc toggle 1 glow AQUA
/npc toggle 1 collision
```

`lines` はTextDisplayホログラムで表示します。`height` は表示位置のYオフセットです。`glow` の色は保存されますが、現時点の表示はPaper標準の発光ON/OFFです。
`collision` をfalseにすると、通常Entity NPCの当たり判定を消せます。

対応済みcustomize:

```txt
ArmorStand: setSmall, setArms, setBasePlate, setInvisible
Creeper: setPowered
Zombie/Ageable: setBaby
Slime/MagmaCube: setSize
Sheep: setColor, setSheared
Villager: setProfession, setVillagerType
Wolf: setSitting, setTamed, setAngry, setCollarColor
Fox: setFoxType, setSitting, setSleeping, setCrouching
Axolotl: setVariant
```

## Conversations

CLICKまたはRADIUSで会話を再生できます。

```txt
/npc conversation create <name>
/npc conversation remove <name>
/npc conversation set <id> <name> <CLICK|RADIUS>
/npc conversation cooldown <name> <seconds>
/npc conversation radius <name> <radius>
/npc conversation text add <name> <delayTicks> <text...>
/npc conversation text remove <name> <index>
/npc conversation text list <name>
/npc conversation list
```

例:

```txt
/npc conversation create welcome
/npc conversation text add welcome 0 &aこんにちは、%player%さん！
/npc conversation text add welcome 40 &7サーバーへようこそ。
/npc conversation set 1 welcome CLICK
```

## Paths

登録した地点をNPCがループ移動します。

```txt
/npc path create <name>
/npc path point <name>
/npc path set <id> <name>
/npc path speed <name> <speed>
/npc path loop <name> <true|false>
/npc path delete <name>
/npc path list
```

例:

```txt
/npc path create lobby
/npc path point lobby
/npc path point lobby
/npc path speed lobby 0.25
/npc path set 1 lobby
```

## Data Files

```txt
plugins/NehaNPCs/config.yml
plugins/NehaNPCs/messages.yml
plugins/NehaNPCs/npcs.yml
plugins/NehaNPCs/conversations.yml
plugins/NehaNPCs/paths.yml
```

## Permissions

`nehanpcs.admin` が全権限を含みます。サブコマンド別の権限も `plugin.yml` に定義済みです。

## Boundary

ProtocolLib packetでPLAYER NPC、skin、mirror、fake spawn/despawn/tablist制御、USE_ENTITY click処理を実装しています。Minecraft/ProtocolLibの細かなpacket仕様差が出る場合は、対象サーバーのProtocolLib版に合わせてPacketContainerのフィールド位置を調整してください。
