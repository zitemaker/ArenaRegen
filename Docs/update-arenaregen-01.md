# ArenaRegen arenaregen-01

Status: Verified
User problem: Arenas in Multiverse worlds fail to load after server restarts when worlds load after ArenaRegen. Server owners see missing arenas and have no command to reload without rebooting.
Chosen change: Add Multiverse-Core softdepend in plugin.yml, add WorldLoadEvent listener to auto-load deferred arenas when worlds load, fix deferred retry logic and race conditions in RegionData, provide thread-safe PlayerMoveListener bounds, and add /arenaregen reloadarenas command with accurate reload counters and list diagnostics.
Accepted behavior: 
1. Multiverse-Core loads before ArenaRegen when present.
2. If a world loads after startup, WorldLoadEvent detects it and automatically loads deferred arena block data.
3. RegionData does not lock itself into a permanent loadFailed state when a world is temporarily not yet available.
4. An admin running `/arenaregen reloadarenas` reloads all `.datc` files from disk and reports loaded, deferred, and failed counts.
5. `/arenaregen list` clearly shows if an arena is loaded, deferred (waiting for world), or failed.
Source: repo zitemaker/ArenaRegen, base revision fbe5de492fe18cf2a280ba7bfa986c6711e9c8b1
Writer: Antigravity
Changed files:
- build.gradle
- src/main/resources/plugin.yml
- src/main/resources/messages.yml
- src/main/java/com/zitemaker/ArenaRegen.java
- src/main/java/com/zitemaker/commands/ArenaRegenCommand.java
- src/main/java/com/zitemaker/helpers/RegionData.java
- src/main/java/com/zitemaker/listeners/PlayerMoveListener.java
- src/main/java/com/zitemaker/listeners/WorldLoadListener.java
- src/test/java/com/zitemaker/RegionDataLoadStateTest.java
- Docs/update-arenaregen-01.md
Checks:
- Maven test (`mvn test`): 4 tests passed, 0 failures, BUILD SUCCESS.
- Maven package (`mvn package`): Legacy shaded jar created successfully.
- Gradle test (`./gradlew test --rerun`): 4 tests passed, 0 failures, BUILD SUCCESSFUL.
- Gradle shadowJar (`./gradlew shadowJar`): Modern shadow jar created successfully.
Independent review: Approved by independent research subagent (conversation 5b3963b7-7a56-470b-9a91-db6a1f87b57c). All initial findings (counters, listing, thread safety, synchronization, test assertions) resolved and re-verified.
Minecraft check: Unit and build verification complete. Live Multiverse-Core server check pending operator testing.
Packet: output/release-packets/ArenaRegen-1.7.2-draft (Status: Draft)
Next action: Operator completes live Multiverse server check and follows checklist.md.
