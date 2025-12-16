# Seating Module

Provides staff-facing CRUD for seating charts and public read APIs for chart structures. Implements the `SeatingApi`
port used by booking/event flows.

## Core concepts

- **SeatingChart**: canvas bounds + background image.
- **Zones**: hierarchical containers; prefix seat/table/GA codes.
- **Seats / Tables / GA areas**: bookable inventory; codes are immutable business keys.
- **Landmarks**: visual-only elements.

## Architecture: DRY DTO strategy

The seating chart structure (zones/seats/tables/GA/landmarks) is represented by a **single canonical shape**
defined in `seating-api` module:

- `ZoneStructureDto` (recursive hierarchy)
- `SeatStructureDto`, `TableStructureDto`, `GaAreaStructureDto`, `LandmarkDto`

Both **staff** and **public cached** endpoints return these structures wrapped in different envelopes:

- Staff: `SeatingChartDetailedResponse` (includes `venueId`, `createdAt`, `updatedAt`)
- Public: `StaticChartStructureResponse` (includes `chartId`, `chartName`, cache-friendly)

This ensures consistency, eliminates duplication, and keeps the recursive payload shape identical across all endpoints.

## Invariants / safety

- **Seat codes are immutable**; sessions/bookings refer to `seatId`, but 3rd parties use `seatCode`. Do not change
  row/number/zone/table for existing seats.
- **Layout replace is disabled**. For major changes, **clone** the chart to get new IDs but keep codes.
- **Visual edits** are allowed in place (position/rotation/color/boundaries/landmarks).
- When a chart is **in use** by events/sessions, inventory semantics are frozen: do not change seat flags/category,
  table
  category, or GA capacity/category in place; **clone** instead.
- **Deletion is guarded**: charts cannot be deleted if referenced by events/sessions (`EventApi.seatingChartInUse`).

## Staff-facing endpoints

- `POST /api/v1/staff/venues/{venueId}/seating-charts` — create chart (metadata).
- `POST /api/v1/staff/venues/{venueId}/seating-charts/layout` — create chart with layout.
- `PUT  /api/v1/staff/venues/{venueId}/seating-charts/{chartId}` — update chart metadata (name/width/height/background).
- `PUT  /api/v1/staff/venues/{venueId}/seating-charts/{chartId}/layout` — **disabled**; clone instead.
- `POST /api/v1/staff/venues/{venueId}/seating-charts/{chartId}/clone` — clone chart for major changes (same codes, new
  IDs).
- `PATCH /api/v1/staff/venues/{venueId}/seating-charts/{chartId}/visuals` — visual-only updates (
  zones/seats/tables/GA/landmarks).
- `GET   /api/v1/staff/venues/{venueId}/seating-charts` — list.
- `GET   /api/v1/staff/venues/{venueId}/seating-charts/details` — paged list.
- `GET   /api/v1/staff/venues/{venueId}/seating-charts/{chartId}` — detailed structure.
- `DELETE /api/v1/staff/venues/{venueId}/seating-charts/{chartId}` — delete (blocked if chart in use).

## Public endpoints

- `GET /api/v1/seating-charts/{chartId}` — detailed chart (staff/public view).
- `GET /api/v1/seating-charts/{chartId}/structure` — static structure (zones/tables/seats/GA/landmarks), cache-friendly.
- Lookups by section/table/GA/seat ID or code.

## DTOs / payloads

- Layout create: `SeatingChartLayoutRequest` (zones, tables, gaAreas, seats).
- Visual update: `SeatingChartVisualUpdateRequest` (zones/seats/tables/gaAreas/landmarks; no code/relationship changes).
- Clone: `CloneSeatingChartRequest` (name, optional backgroundUrl).
- Clone: `CloneSeatingChartRequest` (name only; background and transforms are inherited to keep visual consistency).
- Background image/transform can be changed **only** via chart metadata update (not clone, not visuals).

**Unified structure DTOs** (consistency across endpoints):

- Staff GET and public cached GET both use `ZoneStructureDto`, `SeatStructureDto`, `TableStructureDto`,
  `GaAreaStructureDto` from `seating-api` module for DRY.
- Only the top-level envelope differs:
    - Staff: `SeatingChartDetailedResponse` (includes venueId, timestamps)
    - Public cached: `StaticChartStructureResponse` (includes chartId/chartName, no timestamps)

## Event/booking integration

- Event sessions store **seatId**; dynamic state lives in `SessionSeatConfig` (event module).
- Seating exposes `SeatingApi` for seat/GA/table info and static structures.
- Seat/Table/GA code changes are forbidden; they would break 3rd-party seatCode references.
- Destructive operations are blocked when `EventApi.seatingChartInUse(chartId)` is true.

## Workflows

1) **New chart**: create (metadata) or create with layout.
2) **Price setup**: event module auto-generates templates per category.
3) **Minor edits**: call `PATCH /visuals`.
4) **Major redesign**: clone chart, edit clone, point new sessions to the clone; keep old sessions on old chart.
5) **Deletion**: only if no events/sessions reference the chart.

## Testing

```
./gradlew :seating:test
./gradlew :event:test   # for EventApi seatingChartInUse wiring
```

## Notes

- Keep DB uniqueness: codes unique per zone/table/GA constraints already enforced.
- Add integration tests when extending endpoints; current unit tests cover clone, visual updates, and delete guard.

