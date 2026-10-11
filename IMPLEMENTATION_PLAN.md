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
- `BlockScanner` / `HighlightCache`: main-thread incremental chunk scanning and per-chunk match storage.
- `HighlightRenderer`: state-accurate shape extraction and through-wall GPU line rendering.
- `HighlightScreen`: Minecraft-native search, two scrollable lists, selection controls, and settings.
- `HighlightHud`: optional unobtrusive status indicator.

## Scanning and caching

1. While highlighting is enabled and a client world exists, the scanner discovers loaded chunks inside the configured radius around the player, queueing the player's chunk and then expanding outward so nearby matches are found first.
2. Each chunk is scanned over multiple client ticks with a fixed block budget, starting at the player's Y-level so nearby matches are found promptly. Partial matches are published while scanning continues; world and chunk access never leaves the client thread.
3. A completed scan replaces that chunk's match set atomically. Periodic refreshes catch state changes even when a renderer-specific block update callback is unavailable.
4. Unloaded chunks are removed from the cache. World/dimension changes reset all chunk state, preventing coordinates from one dimension leaking into another.
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
