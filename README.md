<p align="center">
<a href="https://github.com/zitemaker/ArenaRegen/releases/"><img src="https://img.shields.io/github/downloads/zitemaker/ArenaRegen/latest/total.svg" alt="Github Downloads"></a>
<a href="https://www.spigotmc.org/resources/arenaregen.123731/"><img src="https://img.shields.io/spiget/downloads/123731?label=Spigot%20Downloads" alt="Spigot downloads"></a>
<a href="https://www.spigotmc.org/resources/arenaregen.123731/"><img src="https://img.shields.io/spiget/rating/123731" alt="Spigot rating"></a>

</p>

<p align="center">
<a href="https://github.com/zitemaker/ArenaRegen/releases/latest"><img src="https://img.shields.io/github/release/zitemaker/ArenaRegen.svg" alt="Current Release"></a>
<a href="https://github.com/zitemaker/ArenaRegen/graphs/contributors"><img src="https://img.shields.io/github/contributors/zitemaker/ArenaRegen.svg" alt="Contributors"></a>
<a href="https://github.com/zitemaker/ArenaRegen/blob/master/LICENSE"><img src="https://img.shields.io/github/license/zitemaker/ArenaRegen.svg" alt="License"></a>
</p>

<p align="center"><a href="https://discord.gg/HkTQz3xWJc"><img src="https://discord.com/api/guilds/1341770518684241991/embed.png" alt="Discord embed"></a></p>
<p align="center"><a href="https://github.com/zitemaker/ArenaRegen/releases/latest/"><img src="https://img.shields.io/badge/DOWNLOAD-LATEST-success?style=for-the-badge" alt="download badge"></a></p>

This is the official repository for [ArenaRegen](https://zitemaker.tebex.io/) (Minecraft plugin).

Fight. Destroy. Regenerate. Repeat!

## :telescope: Compatibility

**WE DO NOT SUPPORT FORGE / FABRIC**

Pick **one** JAR for your server. Do not install both.

| JAR | Servers | Java | Build |
|-----|---------|------|-------|
| **ArenaRegen-1.7.1-MODERN.jar** | Paper **26.2** (optimized NMS) | **25+** | Gradle |
| **ArenaRegen-1.7.1-LEGACY.jar** | Spigot/Paper **1.18 – 1.21.x** (Bukkit API) | **17+** | Maven |

### Which JAR should I use?

- Running **Paper 26.2** → use **MODERN**
- Running **1.18 – 1.21** (or Spigot without Paper NMS) → use **LEGACY**

### Build either JAR

```bash
# Modern (Paper 26.2) — requires JDK 25
./gradlew build
# → build/libs/ArenaRegen-1.7.1-MODERN.jar

# Legacy (1.18–1.21) — requires JDK 17+
mvn -B clean package
# → target/ArenaRegen-1.7.1-LEGACY.jar
```

## :link: Links

- [BuiltByBit](https://builtbybit.com/resources/arenaregen.63058/)
- [ArenaRegen+](https://www.spigotmc.org/resources/arenaregen-instantly-reset-pvp-arenas-with-1-command.124624/)
- [SpigotMC](https://www.spigotmc.org/resources/123731)
- [Discord](https://discord.gg/HkTQz3xWJc)
- [Website](https://zitemaker.tebex.io)

## 📥 How to install? / Installation / Setup

You can [read on our official documentation](https://falcona.gitbook.io/arenaregen) how to
install ArenaRegen. Make sure to follow all steps before reporting issues!

## 🌈 Community

Feel free to join our Discord community server:

[![Discord Banner](https://discord.com/api/guilds/1341770518684241991/widget.png?style=banner2)](https://discord.gg/HkTQz3xWJc)

## :family: Authors

See [Contributors](https://github.com/zitemaker/ArenaRegen/graphs/contributors) for a list of people that have
supported this project by contributing.

## :scroll: License

ArenaRegen is licensed under GNU General Public License v3.0. Please
see [`LICENSE`](https://github.com/zitemaker/ArenaRegen/blob/master/LICENSE) for more info.
