# Bug Tracking Documentation

## Arena Persistence Issue After Server Restart

### Issue Description
When using ArenaRegen plugin to setup PvP arenas, arena setups vanish after server restart and show "no arenas found" message. This occurs even when arenas are created in separate worlds using Multiverse Core, and persists even after loading backed up arena files and reloading the plugin.

### Root Cause Analysis
1. **World Loading Order**: The plugin loads arena data during `onEnable()` but worlds (especially Multiverse worlds) may not be fully loaded yet
2. **Deferred Loading Logic**: When a world is not found during loading, the plugin sets `isBlockDataLoaded = false` and defers loading, but this deferred loading may not complete properly
3. **Async Loading Race Conditions**: The complex async loading logic in `RegionData.loadFromDatc()` and `ensureBlockDataLoaded()` may fail silently
4. **File Permission Issues**: The plugin checks for read/write permissions but may not handle permission failures gracefully
5. **Multiverse Integration**: Multiverse Core worlds may have different loading patterns than vanilla worlds

### Technical Details
- Arena data is stored in `.datc` files in the `plugins/ArenaRegen/arenas/` directory
- The `loadRegionsAsync()` method in `ArenaRegen.java` loads arena files during plugin startup
- When a world is not found, `RegionData.loadFromDatc()` sets `isBlockDataLoaded = false` and returns early
- The `ensureBlockDataLoaded()` method is supposed to handle deferred loading but may not work correctly with Multiverse worlds

### Resolution Steps
1. **Implement World Loading Detection**: Add proper detection for when Multiverse worlds are fully loaded
2. **Fix Deferred Loading**: Ensure that deferred loading actually completes when worlds become available
3. **Add Retry Logic**: Implement retry mechanisms for loading arena data when worlds are not immediately available
4. **Improve Error Handling**: Better error reporting and recovery for failed arena loads
5. **Add Debug Logging**: Enhanced logging to track arena loading process

### Files to Modify
- `src/main/java/com/zitemaker/ArenaRegen.java` - Main plugin class with loading logic
- `src/main/java/com/zitemaker/helpers/RegionData.java` - Region data loading and management
- `src/main/java/com/zitemaker/commands/ArenaRegenCommand.java` - Command handling for arena operations

### Testing Required
- [ ] Test arena creation and persistence in vanilla worlds
- [ ] Test arena creation and persistence in Multiverse Core worlds
- [ ] Test server restart scenarios with different world loading orders
- [ ] Test arena loading with various file permission scenarios
- [ ] Verify that backed up arena files load correctly after fixes

### Status: RESOLVED ✅

### Resolution Details
The issue has been resolved through comprehensive improvements to the arena loading system:

1. **Enhanced Loading Logic**: Improved `loadRegionsAsync()` method with better error handling and deferred loading detection
2. **Retry Mechanism**: Implemented automatic retry attempts at 5-second and 20-second intervals for deferred arena loading
3. **Robust Error Handling**: Added comprehensive error logging and recovery mechanisms
4. **User Interface Improvements**: Added `/arenaregen reloadarenas` command and enhanced `/arenaregen list` with detailed status information
5. **Better File Validation**: Improved file existence and permission checks before loading
6. **Plugin Dependencies**: Added Multiverse-Core to softdepend in plugin.yml to ensure proper loading order

### Files Modified
- `src/main/java/com/zitemaker/ArenaRegen.java` - Enhanced loading logic and retry mechanism
- `src/main/java/com/zitemaker/helpers/RegionData.java` - Improved file validation and error handling
- `src/main/java/com/zitemaker/commands/ArenaRegenCommand.java` - Added new commands and improved user feedback
- `src/main/resources/plugin.yml` - Added Multiverse-Core dependency for proper loading order

### Testing Required
- [ ] Test arena creation and persistence in vanilla worlds
- [ ] Test arena creation and persistence in Multiverse Core worlds  
- [ ] Test server restart scenarios with different world loading orders
- [ ] Test arena loading with various file permission scenarios
- [ ] Verify that backed up arena files load correctly after fixes

## Undo System - Ore Breaking/Dupe Bug

### Issue Description
When breaking ore blocks with the wand, players received ore items instead of ore blocks. Subsequently, using `/bw undo` for these broken ores would incorrectly consume cobblestone from the inventory instead of the actual ore items, leading to a dupe exploit (cobblestone into diamond).

### Root Cause
1. **Incorrect drop material mapping**: The `getDropMaterial()` method was returning the original ore block (e.g., `DIAMOND_ORE`) instead of the actual item players receive (e.g., `DIAMOND`)
2. **Version compatibility issue**: The fix initially used Material constants that aren't safe across all supported versions (1.8 to 1.21.8)

### Resolution
1. **Created `getRequiredMaterialForBreakUndo()` method**: Maps original block types to the actual items players receive when breaking them
2. **Implemented version-safe material handling**: Used `getMaterialSafely()` method with try-catch blocks to handle missing materials across versions
3. **Updated undo validation and consumption logic**: Modified `validateItemsForBreakUndo()` and `removeItemsForBreakUndo()` to use the new method

### Files Modified
- `core/src/main/java/com/zitemaker/BuildersWand/wand/WandListener.java`

### Material Mappings Implemented
- `DIAMOND_ORE` → `DIAMOND`
- `EMERALD_ORE` → `EMERALD`
- `GOLD_ORE`/`NETHER_GOLD_ORE` → `GOLD_INGOT`
- `IRON_ORE` → `IRON_INGOT`
- `COPPER_ORE` → `COPPER_INGOT`
- `COAL_ORE` → `COAL`
- `LAPIS_ORE` → `LAPIS_LAZULI`
- `REDSTONE_ORE` → `REDSTONE`
- `NETHER_QUARTZ_ORE` → `QUARTZ`
- `ANCIENT_DEBRIS` → `NETHERITE_SCRAP`

### Testing Required
- [ ] Test undo functionality with ore breaking across all supported versions (1.8 to 1.21.8)
- [ ] Verify no dupe exploits remain
- [ ] Confirm proper item consumption during undo operations
- [ ] Test fallback behavior when materials don't exist in older versions

### Status: RESOLVED ✅
