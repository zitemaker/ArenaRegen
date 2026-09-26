## Added
- Added Multiverse-Core softdepend to ensure secondary worlds load before ArenaRegen.
- Added WorldLoadEvent listener to detect late world loads and load deferred arena data automatically.
- Added /arenaregen reloadarenas (/ar reloadarenas) command under arenaregen.reload permission to reload all arena files from disk without restarting the server.
- Added detailed loaded, deferred, and failed counters to /ar reloadarenas and /ar list.

## Fixed
- Fixed arena block data loading failure when arenas are in Multiverse worlds that load after server startup.
- Fixed RegionData permanently locking into a failed state if its world was temporarily not loaded.
- Fixed potential concurrency race conditions during parallel deferred arena block loading.
- Fixed thread-safety in PlayerMoveListener arena boundaries cache.
- Fixed typo in reload feedback message color code.

## Changed
- Updated /arenaregen list and /arenaregen info to show explicit status for failed, deferred, and loaded arenas.
