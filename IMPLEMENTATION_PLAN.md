# Block Highlighter implementation plan

## Target and verified APIs

- Minecraft Java Edition 1.21.11, Java 21, Fabric Loader 0.19.3, Fabric API 0.141.6+1.21.11, Yarn 1.21.11+build.6, and Fabric Loom 1.17.17.
- The block selector is populated from the live `Registries.BLOCK` registry. No vanilla block list is maintained in source.
- Block detection reads client-loaded `WorldChunk` instances only. It uses `BlockState#getOutlineShape(world, pos)` and renders every edge of the resulting `VoxelShape`.
- Rendering uses the 1.21.11 `WorldRenderEvents.END_MAIN` drawing hook and a dedicated GPU render pipeline whose depth test is `NO_DEPTH_TEST`. Drawing at the end of the main world pass ensures the direct main-framebuffer draw is not overwritten by later terrain/translucent work. The rest of Minecraft's depth state remains unchanged.

## Architecture

- `BlockHighlighterClient`: client bootstrap, lifecycle, command/keybind and renderer wiring.
- `HighlightConfig` / `ConfigManager`: versioned JSON configuration and atomic writes.
- `SelectedBlockManager`: registry-ID based selection operations and validation.
- `BlockSearchIndex` / `SearchMatcher`: one-time registry index plus cached instant filtering.
- `BlockScanner` / `ScanRegion` / `HighlightCache`: bounded, palette-aware, main-thread discovery and incremental per-chunk match storage.
- `RefreshCycle` / `PendingBlockUpdates`: monotonic deadlines and changed-position work that takes priority over background scans.
- `ClientWorldMixin`: collects local/predicted and authoritative client block updates without accessing the world off-thread.
- `HighlightRenderer`: state-accurate shape extraction and through-wall GPU line rendering.
- `HighlightScreen`: Minecraft-native search, two scrollable lists, selection controls, and settings.
- `HighlightHud`: optional unobtrusive status indicator.

## Scanning and caching

1. While highlighting is enabled and a client world exists, the scanner discovers loaded chunks intersecting the radius bounding box, ordered nearest-first. Each task is clipped horizontally and vertically; section palettes skip irrelevant sections unless cached matches need validation.
2. Block-change notifications are deduplicated. At each monotonic refresh deadline, eligible changed positions are processed before background tasks, using the same 4,096-position / 2 ms client-tick budget. Background tasks cannot publish changes still awaiting a deadline.
3. Scan results update individual cached positions incrementally. Periodic bounded rescans catch changes not represented by individual notifications. Interval changes cancel old periodic tasks and rebase pending updates and the timer, without clearing existing highlights.
4. Chunk load/replacement and unload events invalidate old chunk state. World/dimension changes reset all state, preventing coordinates from one dimension leaking into another. Movement schedules discovery for newly uncovered regions.
5. Rendering rebuilds a reusable world-coordinate vertex buffer only when cached data, settings, player block position, or a periodic state refresh changes. Each rebuild applies a precise player-distance check and reads the current block state before drawing; selection changes schedule loaded chunks for refresh without a frame-wide world scan.

## Rendering

- The renderer uses the 1.21.11 line shader's screen-space expansion and `POSITION_COLOR_NORMAL_LINE_WIDTH` vertex format through a custom pipeline. The mod-owned shader omits fog because geometry is retained in world coordinates while camera translation is supplied dynamically. The pipeline is registered with the public vanilla pipeline registry API so shader reload/compilation follows Minecraft's normal lifecycle.
- A reusable GPU buffer is built from cached positions. Each state's outline shape is queried from the live world; the shape's complete edge set is emitted, so slabs, stairs, fences, walls, buttons, plants, doors, trapdoors, and similar blocks are not replaced with full cubes. Camera translation is supplied as a dynamic uniform so ordinary camera motion does not rebuild geometry.
- Empty outline shapes fall back to collision shape and then a conservative block box, except air states, which are skipped to avoid turning a selection of air into an unusable volume of outlines.
- The custom pipeline disables depth testing only for this draw pass and writes no depth. It does not globally change OpenGL/Minecraft render state. A three-buffer mapped ring prevents the GPU upload from stalling the following frame.

## Persistence and UI

- Selections are stored by registry ID under `.minecraft/config/block-highlighter/config.json`.
- The loader validates IDs against the current block registry, ignores removed entries with a warning, backs up malformed files, and uses a temporary file plus atomic move where supported.
- The screen caches filtered results until the search query changes, supports registry IDs and localized names, shows item icons where a block has an item, and has independent scroll positions for available and selected blocks. It opens through the client-side `/blockhighlighter` command (with `/bh` alias).
- Global color, opacity, line width, radius, enabled state, and HUD visibility are saved with the same configuration.

## Verification

- Unit tests cover color parsing, selection/config round trips, duplicate handling, malformed JSON fallback, and case-insensitive/fuzzy search scoring.
- `gradlew build` is the required build check.
- A development-client launch is attempted when the environment supports it. If graphical launch is unavailable, the README contains exact manual verification steps and the final report records that limitation.

## Deliberate scope boundary

Named presets and JSON preset import/export are not part of the first stable implementation. They are independent of the core scan/render path and are documented as future work so they do not add instability to the mandatory client-side highlighter behavior.
