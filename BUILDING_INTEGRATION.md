# OSM Building Footprint Integration

## Overview

Semi-transparent 3D building shapes from OpenStreetMap are now rendered in the AR view. Building footprint polygons and heights are fetched from the Overpass API, extruded into 3D meshes, and displayed alongside existing user-placed objects.

## What Changed

### New Files

| File | Module | Purpose |
|------|--------|---------|
| `core/src/com/mygdx/game/notbaza/Building.kt` | core | `LatLon` and `Building` data classes |
| `core/src/com/mygdx/game/BuildingMeshGenerator.kt` | core | Converts `Building` polygon into extruded LibGDX `Model` (walls + roof) |
| `android/src/com/mygdx/game/network/OverpassClient.kt` | android | HTTP client for Overpass API, fetches buildings within 300m |
| `android/src/com/mygdx/game/network/BuildingCache.kt` | android | SharedPreferences JSON cache with 24h TTL |

### Modified Files

| File | Changes |
|------|---------|
| `core/src/com/mygdx/game/MyGdxGame.kt` | Added `buildingInstances`, `buildingModels`, `buildingsVisible` fields; `setBuildings()` method; renders buildings in `modelBatch` block; disposes in `dispose()` |
| `android/src/com/mygdx/game/activities/AndroidLauncher.kt` | Added async coroutine after game init to fetch buildings (cache-first), delivers to GL thread via `Gdx.app.postRunnable` |

## Architecture Decisions

- **Buildings are separate from user objects** — stored in their own `buildingInstances` list, not mixed into `instances`. This means buildings are automatically excluded from touch selection (existing `getObject()` only iterates `instances`).
- **Vertices baked in local coordinates** — each building mesh has vertex positions pre-computed as ECEF offsets from camera. Transform is identity. Works because the view matrix handles GPS drift uniformly.
- **No new dependencies** — uses existing `HttpURLConnection`, `Gson`, and LibGDX `ModelBuilder`/`MeshPartBuilder`/`EarClippingTriangulator`.
- **Async loading** — AR view launches immediately, buildings appear once fetched (~2-5 seconds).
- **300m radius** matches camera far plane (`cam.far = 300f`).

## How It Works

1. AR view opens, game initializes normally
2. A coroutine checks `BuildingCache` (SharedPreferences, keyed by ~300m grid cell)
3. If cache miss or stale (>24h), fetches from `https://overpass-api.de/api/interpreter` with query: `way["building"](around:300,{lat},{lon})`
4. Response is parsed — height comes from OSM tags: `height` > `building:height` > `building:levels * 3` > default 10m
5. `BuildingMeshGenerator` extrudes each polygon: walls as quads (2 triangles per edge), roof via ear-clipping triangulation
6. Results delivered to GL thread, rendered as semi-transparent gray (`Color(0.6, 0.6, 0.6, 0.35)`)

## Known Limitations

- **Building altitude**: Buildings are placed at altitude=0 (sea level). On hilly terrain they may float or sink. Terrain elevation is out of scope.
- **Polygon winding**: OSM polygons may be CW or CCW. Some building faces may appear inside-out. Could be fixed by computing signed area and flipping, or disabling face culling for buildings.
- **Float precision**: ECEF coordinates are ~6.4M meters. Subtraction of nearby points may lose precision. Intermediate math uses Double, cast to Float at the end.
- **Degenerate polygons**: Buildings that fail mesh generation are silently skipped (try-catch per building).
- **No multipolygon support**: Only `way` elements are fetched, not `relation` multipolygons (complex buildings with holes).

## Testing

1. Build: `./gradlew build`
2. Install: `./gradlew android:installDebug`
3. Open AR view in any urban area with OSM building data
4. Verify: semi-transparent 3D shapes appear within ~5 seconds
5. Verify: existing user-placed cubes still render correctly
6. Verify: buildings are not selectable via touch
7. Verify: second launch in same area uses cache (no network delay)
