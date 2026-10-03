# ref.industrial_union.small_craft.base.v1 — generation attempt 001

**Status:** REJECTED — target/context drift  
**Reference ID:** `ref.industrial_union.small_craft.base.v1`  
**Source generation ID:** `af37623a-c4c0-4a60-9ac9-088a2ea75fd2`

## Requested operation

Generate the next `GENERATE_5_SELECT_1` candidate for the Industrial Union carrier small-craft base:

- one spacecraft only;
- strict top-down orthographic;
- forward/right orientation;
- 2:1 production aspect;
- transparent background;
- standardized modular Union construction;
- no fit-specific weapon or shield package;
- no Imperial central-citadel language.

## Actual output

The connected image backend returned an Imperial-style female medic character reference sheet rather
than a spacecraft.

## Decision

The output is a hard reject and has zero reference authority.

It is not added to the candidate set, is not normalized, is not scored and does not change the
manifest row. The Industrial Union small-craft base remains `PLANNED`, `geometry_frozen=false`.

## Workflow response

Do not repeatedly consume candidate slots while the backend is demonstrably targeting unrelated
character context. Continue the deterministic PROMOTE_EXISTING freeze work and resume this
`GENERATE_5_SELECT_1` family only when the image backend is producing the requested object class
reliably.
