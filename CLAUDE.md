# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android AR application that displays 3D objects overlaid on the device camera feed using LibGDX. The app allows users to "see through walls" - objects placed at real-world geographic coordinates are rendered as if there were no obstacles between the user and the object. This enables visualization of where something is located even when not visible in the real world.

Objects are positioned using real-world geographic coordinates (latitude, longitude, altitude) converted to Earth-Centered Earth-Fixed (ECEF) Cartesian coordinates for accurate placement in 3D space.

### Touch Controls in AR View

When no object is selected:
- **Horizontal swipe**: Rotates the view left/right (manual compass correction if phone's orientation is inaccurate)

Note: Physical distance to objects is determined solely by GPS coordinates. There is no gesture to change distance.

## Build Commands

```bash
# Build the project
./gradlew build

# Install debug APK on connected device
./gradlew android:installDebug

# Run app on device (after install)
./gradlew android:run

# Clean build
./gradlew clean
```

## Setup

Google Maps API key must be set in `local.properties`:
```
MAPS_API_KEY=your_api_key_here
```

## Architecture

### Module Structure

- **core** - Platform-independent LibGDX game logic and rendering interfaces
- **android** - Android-specific implementation including UI (Jetpack Compose), ARCore, and database

### Key Components

**3D Rendering (core/src/com/mygdx/game/MyGdxGame.kt)**
- Main LibGDX ApplicationAdapter managing 3D scene rendering
- Converts geographic coordinates (lat/lon/alt) to Cartesian using `geoToCartesian()`
- Handles object selection, transformation (move/rotate/scale), and touch input
- Receives orientation matrix from `OnDrawFrame.lastHeadView` interface
- Personal objects with OSM polygon data rendered as 3D building meshes (via `BuildingMeshGenerator`)
- Dual distance filtering: separate min/max ranges for personal objects and nearby buildings

**ARCore Integration (android/src/com/mygdx/game/arcore/)**
- `ARCoreSessionManager` - Manages ARCore session lifecycle, provides camera view matrix for drift-free orientation tracking
- `ARCoreBackgroundRenderer` - Renders ARCore camera background as OpenGL texture
- Device orientation comes exclusively from ARCore

**Custom LibGDX Integration (android/src/com/mygdx/game/overr/)**
- `AndroidApplicationOverrided` - Custom AndroidApplication subclass that AndroidLauncher extends for Compose integration

**Android Activities (android/src/com/mygdx/game/activities/)**
- `MainActivity` - Entry point with Jetpack Compose UI, manages object list
- `AndroidLauncher` - LibGDX activity with ARCore integration and edit mode UI
- `MapViewer` - Google Maps Compose for placing objects geographically
- `MagnetExperiment` - Standalone activity for testing magnetometer/rotation sensors (debug tool)

**UI Layer (android/src/com/mygdx/game/ui/)**
- Jetpack Compose screens in `screens/` (MainScreen, MapViewerScreen, AROverlayScreen, MagnetExperimentScreen)
- Dialogs in `dialogs/` (AddOrEditObjectDialog with place search and OSM building auto-fetch)
- Components in `components/` (ObjectListItem, BuildingPreviewRenderer)
- Material 3 theming in `theme/`
- `OrientationIndicator` - Custom View for compass overlay display
- AR overlay has a settings panel (gear icon) with FOV controls and dual distance range sliders for objects/buildings

**ViewModel Layer (android/src/com/mygdx/game/viewmodel/)**
- `MainViewModel` - Manages object CRUD operations and camera state via StateFlow
- `ARViewModel` - AR-specific state management

**Network Layer (android/src/com/mygdx/game/network/)**
- `OverpassClient` - Fetches OSM building footprints; includes `fetchBuildingAtPoint()` with point-in-polygon test for auto-associating objects with buildings
- `BuildingCache` - Caches OSM building data locally

**Data Layer**
- `baza/Objekt` - Room entity for persistent storage (x=latitude, y=longitude, z=altitude, osmId, polygonJson, heightMeters, minHeightMeters)
- `baza/UserBuilding` - Room entity for user-modified building heights
- `baza/Daovi` - DAO interface for database operations
- `notbaza/Objekt` - Runtime model with computed Cartesian offsets (diffX/Y/Z), LibGDX Color, and deserialized polygon data
- `notbaza/Building` - Runtime building model with `LatLon` polygon
- `DatastoreRepository` - Camera position persistence via DataStore Preferences

### Coordinate System

Objects use geographic coordinates internally but are rendered using ECEF-based Cartesian offsets:
- `x` = latitude, `y` = longitude, `z` = altitude
- `diffX/Y/Z` = computed Cartesian offset from camera position

### Database

Room database (`database-name`) with `Objekt` and `UserBuilding` tables (version 3). Uses KSP for annotation processing. Camera position persisted via DataStore. Objects may optionally store OSM building polygon data (osmId, polygonJson, heightMeters, minHeightMeters) for 3D building rendering.

## Key Dependencies

- LibGDX 1.13.1 - 3D rendering
- ARCore 1.44.0 - AR tracking and camera
- Jetpack Compose (BOM 2024.12.01) - UI
- Room 2.6.1 - Local database
- Google Maps Compose 4.3.0 - Map display
- Google Play Services Location 21.1.0 - GPS coordinates
- DataStore Preferences 1.1.1 - Camera position persistence
- Gson 2.10.1 - JSON serialization
