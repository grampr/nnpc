# NehaNPCs

NehaNPCs is a ZNPCs-style NPC management plugin for Paper 1.21.x. It supports persistent entity NPCs, packet-based player NPCs, skins, click actions, conversations, paths, equipment, and appearance settings.

Japanese documentation follows the English section.

## Requirements

- Paper 1.21.x
- Java version required by your Paper build
- ProtocolLib (required)
- Paper 1.21.11 requires a compatible ProtocolLib development build. ProtocolLib 5.4.0 does not support PLAYER NPC packets on that server version.

## Build

```sh
mvn package
```

Output: `target/nehanpcs-0.1.0.jar`

## Commands

```text
/npc create <id> <type> <name...>
/npc delete <id>
/npc list
/npc teleport <id>
/npc move <id>
/npc skin <id> <username>
/npc lines <id> <text...>
/npc height <id> <height>
/npc equip <id> <HAND|OFFHAND|HELMET|CHESTPLATE|LEGGINGS|BOOTS>
/npc type <id> <type>
/npc customize <id> <key> <value>
/npc toggle <id> <look|holo|glow|mirror|collision> [color]
/npc action <add|list|remove|cooldown> ...
/npc conversation <create|remove|set|cooldown|radius|text|list> ...
/npc path <create|point|set|speed|loop|delete|list> ...
/npc save
/npc reload
```

`/znpcs` is an alias for `/npc`. Command keywords remain language-neutral; command feedback and usage messages are localized.

## Player NPCs

```text
/npc create 1 player Guide
/npc skin 1 Notch
/npc toggle 1 mirror
```

PLAYER NPCs use ProtocolLib packets for spawning, despawning, tab-list control, skins, and click detection. `mirror` displays each viewer's own skin.

## Actions

```text
/npc action add <id> <CMD|CONSOLE|CHAT|MESSAGE|SERVER> <content...>
/npc action list <id>
/npc action remove <id> <actionId>
/npc action cooldown <id> <actionId> <seconds>
```

Available placeholders: `%player%`, `%uuid%`, `%world%`, `%x%`, `%y%`, `%z%`, `%npc_id%`, `%npc_name%`.

Both action forms below transfer the clicking player to `lobby`:

```text
/npc action add 1 SERVER lobby
/npc action add 1 CMD server lobby
```

For Velocity, enable `bungee-plugin-message-channel = true` in `velocity.toml`. The backend server must use the `BungeeCord` plugin messaging channel configured under `server-transfer.channel`.

## Languages

New installations use each player's Minecraft language automatically:

```yaml
settings:
  language: auto
  fallback-language: en
```

Bundled languages:

- English: `messages_en.yml`
- Japanese: `messages.yml`

To force one language for everyone, set `settings.language` to `en`, `ja`, `en_US`, or `ja_JP`. To add another language, create a file such as `plugins/NehaNPCs/messages_de_DE.yml`. Missing translations fall back to `settings.fallback-language`.

## Data Files

```text
plugins/NehaNPCs/config.yml
plugins/NehaNPCs/messages.yml
plugins/NehaNPCs/messages_en.yml
plugins/NehaNPCs/npcs.yml
plugins/NehaNPCs/conversations.yml
plugins/NehaNPCs/paths.yml
```

NPCs, actions, conversations, paths, skins, hologram settings, and locations are persisted across restarts.

## Permissions

`nehanpcs.admin` grants every NehaNPCs permission. Individual command permissions are listed in `plugin.yml`.

---

## 日本語

NehaNPCsはPaper 1.21.x向けのNPC管理プラグインです。通常エンティティNPCとPLAYER NPC、スキン、クリックアクション、会話、パス移動、装備、見た目設定に対応しています。

### 必要環境

- Paper 1.21.x
- 使用するPaperが要求するJava
- ProtocolLib（必須）
- Paper 1.21.11では対応したProtocolLib Development Buildが必要です。ProtocolLib 5.4.0ではPLAYER NPCのパケットに対応できません。

### 基本操作

```text
/npc create 1 zombie &a案内人
/npc create 2 player Guide
/npc skin 2 Notch
/npc toggle 2 mirror
/npc list
/npc teleport 1
/npc move 1
/npc delete 1
/npc save
/npc reload
```

コマンドのサブコマンド名は全言語で共通です。使い方、成功、エラーなどの表示は、プレイヤーがMinecraftで選択している言語へ自動で切り替わります。

### 言語設定

```yaml
settings:
  language: auto
  fallback-language: en
```

全員を日本語に固定する場合は `language: ja`、英語に固定する場合は `language: en` を指定してください。別の翻訳は `messages_de_DE.yml` のようなファイルを追加すれば利用できます。設定変更後は `/npc reload` を実行します。

### 補足

- NPCの名前や会話文は入力した文字列のまま保存されます。
- PLAYER NPCのスキンは `/npc skin <id> <username>` で設定します。
- `collision` を無効にすると通常エンティティNPCの当たり判定を消せます。
- データはYAMLへ保存され、サーバー再起動後に復元されます。
- Velocityで `/npc action add 1 CMD server lobby` を使う場合は、`velocity.toml` の `bungee-plugin-message-channel = true` を有効にしてください。`SERVER lobby` アクションでも同じように移動できます。
