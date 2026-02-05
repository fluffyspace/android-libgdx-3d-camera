# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android AR application that displays 3D objects overlaid on the device camera feed using LibGDX. Objects are positioned using real-world geographic coordinates (latitude, longitude, altitude) converted to Earth-Centered Earth-Fixed (ECEF) Cartesian coordinates for accurate placement in 3D space.

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
- **android** - Android-specific implementation including UI (Jetpack Compose), sensors, camera, ARCore, and database

### Key Components

**3D Rendering (core/src/com/mygdx/game/MyGdxGame.kt)**
- Main LibGDX ApplicationAdapter managing 3D scene rendering
- Converts geographic coordinates (lat/lon/alt) to Cartesian using `geoToCartesian()`
- Handles object selection, transformation (move/rotate/scale), and touch input
- Receives orientation matrix from `OnDrawFrame.lastHeadView` interface

**ARCore Integration (android/src/com/mygdx/game/arcore/)**
- `ARCoreSessionManager` - Manages ARCore session lifecycle, provides camera view matrix for drift-free tracking (replaces legacy HeadTracker/OrientationEKF)
- `ARCoreBackgroundRenderer` - Renders ARCore camera background

**Android Activities (android/src/com/mygdx/game/activities/)**
- `MainActivity` - Entry point with Jetpack Compose UI, manages object list
- `AndroidLauncher` - LibGDX activity with ARCore integration and edit mode UI
- `MapViewer` - Google Maps Compose for placing objects geographically

**UI Layer (android/src/com/mygdx/game/ui/)**
- Jetpack Compose screens in `screens/` (MainScreen, MapViewerScreen, AROverlayScreen)
- Dialogs in `dialogs/` (AddOrEditObjectDialog with place search)
- Material 3 theming in `theme/`

**ViewModel Layer (android/src/com/mygdx/game/viewmodel/)**
- `MainViewModel` - Manages object CRUD operations and camera state via StateFlow
- `ARViewModel` - AR-specific state management

**Data Models**
- `baza/Objekt` - Room entity for persistent storage (x=latitude, y=longitude, z=altitude)
- `notbaza/Objekt` - Runtime model with computed Cartesian offsets (diffX/Y/Z) and LibGDX Color

### Coordinate System

Objects use geographic coordinates internally but are rendered using ECEF-based Cartesian offsets:
- `x` = latitude, `y` = longitude, `z` = altitude
- `diffX/Y/Z` = computed Cartesian offset from camera position

### Database

Room database (`database-name`) with single `Objekt` table. Uses KSP for annotation processing. Camera position persisted via DataStore.

## Key Dependencies

- LibGDX 1.13.1 - 3D rendering
- ARCore 1.44.0 - AR tracking and camera
- Jetpack Compose (BOM 2024.12.01) - UI
- Room 2.6.1 - Local database
- Google Maps Compose 4.3.0 - Map display
- Google Play Services Location - GPS coordinates
