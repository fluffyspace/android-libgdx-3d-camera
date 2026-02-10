# Object Rework Plan

Objects are no longer rendered as cubes. Every personal object is either a 3D building mesh (from polygon data) or a sphere (when polygon data is missing). This document outlines the phased plan.

---

## Phase 1 — New Object Creation Flow with Building Picker

**Goal:** When creating an object, auto-fetch nearby OSM buildings and let the user pick one.

1. User picks approximate coordinates (existing MapViewer flow).
2. App queries Overpass for buildings within 50m of the dropped pin.
3. If buildings exist, show a **Building Picker Dialog**:
   - 3D preview of each building (rotatable around vertical axis only).
   - Paginate/swipe through candidates, ordered by distance from pin.
   - User picks a building → polygon data (osmId, polygonJson, heightMeters, minHeightMeters) is filled.
   - User can dismiss without picking → polygon data stays empty.
4. If no buildings found, skip dialog — polygon data stays empty.

**Result:** Object is saved with or without polygon data.

---

## Phase 2 — Sphere Rendering for Objects Without Polygon

**Goal:** Replace cube rendering with sphere for objects that have no polygon data.

- Objects with polygon data → render as 3D building mesh (already works).
- Objects without polygon data → render as **sphere** instead of cube.
- Update `MyGdxGame` model creation to use `ModelBuilder().createSphere(...)` as fallback.
- Selected-object highlight model also becomes a sphere.

---

## Phase 3 — AR Object List with Hide/Show

**Goal:** Add an in-AR list of personal objects with visibility control.

1. Add a **"list" button** to the AR overlay UI.
2. Pressing it opens a **bottom sheet / overlay list** of personal objects:
   - Sorted by distance from current camera position.
   - Each row shows: object name, distance, **edit button**, **eye (visibility toggle) button**.
3. **Eye button** hides/shows the object in AR view.
   - Hidden state persisted (Room: add `hidden` boolean column to `Objekt`, migration v3→v4).
   - Hidden objects are skipped during rendering but remain in the list (shown grayed out / with crossed-out eye).
4. **Edit button** selects the object and opens the edit menu (enters edit mode for that object).

---

## Phase 4 — Edit Menu Tabs (Object Editor + Vertices Editor stub)

**Goal:** Restructure the edit mode UI into two tabs.

1. When edit mode is active, show a **tab bar** with two tabs:
   - **Object Editor** — existing transformation controls (move, move vertical, rotate, scale).
   - **Vertices Editor** — placeholder/stub for Phase 5.
2. Object Editor behavior remains unchanged.
3. Vertices Editor tab shows a message like "Coming soon" initially.

---

## Phase 5 — Vertices Editor (AR Point Picking)

**Goal:** Let the user create/edit a building polygon from scratch by picking points in AR space.

1. In Vertices Editor mode, the user points the center of the screen (crosshair) at a real-world point.
2. **ARCore hit-test** determines the 3D position of that point.
3. User taps to **place a vertex** at that position.
4. Vertices are connected in order to form the polygon outline (rendered as lines/wireframe in real-time).
5. Controls:
   - **Add vertex** — tap to place at crosshair hit-test point.
   - **Undo** — remove last vertex.
   - **Close polygon** — connect last vertex to first, finalize the shape.
   - **Set height** — drag gesture or slider to extrude the polygon vertically.
   - **Cancel** — discard all vertices.
   - **Save** — convert vertices to polygonJson, save to object.
6. The placed vertices use geographic coordinates (convert ARCore hit-test world position back to lat/lon/alt).
7. After save, the object re-renders as a 3D building mesh using the new polygon.

This is essentially a simplified Blender-like modeler operating in AR space. Implementation details will be fleshed out in a dedicated phase document.

---

## Phase Order & Dependencies

```
Phase 1 (Building Picker) ── can start immediately
Phase 2 (Sphere Rendering) ── can start immediately, independent of Phase 1
Phase 3 (AR Object List)   ── can start immediately, independent of 1 & 2
Phase 4 (Edit Menu Tabs)   ── depends on Phase 3 (edit button triggers edit mode)
Phase 5 (Vertices Editor)  ── depends on Phase 4 (tab must exist)
```

Phases 1, 2, and 3 are independent and can be implemented in any order or in parallel.
