# Ship heading presentation contract

Status: implemented presentation contract.

Moving ship sprites use the authoritative velocity vector available to the active presentation path. The established sprite convention is nose/forward along local +X, so presentation heading is `atan2(velocityY, velocityX)` and is converted to counter-clockwise degrees only at the renderer boundary.

Generated-world freight and military fleets resolve heading from the exact Stage-20 `LocalPhysicalKinematics` sidecar used for their live position. Ordinary local ECS ships use `TransformComponent.velocity` when available. Static objects and ships below the near-zero velocity threshold keep a neutral zero-degree presentation heading.

This rule is presentation-only. It does not rotate simulation state, change thrust, steering, collision, pathfinding, save data, or movement authority. The UI remains a read-only projection of authoritative kinematics.
