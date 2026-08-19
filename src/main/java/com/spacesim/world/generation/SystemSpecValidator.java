package com.spacesim.world.generation;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.generation.SystemSpec.CelestialAnchor;
import com.spacesim.world.generation.SystemSpec.CelestialKind;
import com.spacesim.world.generation.SystemSpec.LocalSite;
import com.spacesim.world.generation.SystemSpec.SiteKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Physical-integrity validation for deterministic Stage-20B system specifications. */
public final class SystemSpecValidator {
    private SystemSpecValidator() {
    }

    /** Returns all deterministic validation violations without mutating the specification. */
    public static ValidationReport validate(SystemSpec spec) {
        SystemSpec checked = Objects.requireNonNull(spec, "spec");
        List<String> violations = new ArrayList<>();
        validateIdentifiers(checked, violations);
        validateCelestials(checked, violations);
        validateSites(checked, violations);
        validateExtents(checked, violations);
        return new ValidationReport(violations);
    }

    /** Throws when a generated specification violates the Stage-20B physical contract. */
    public static void requireValid(SystemSpec spec) {
        ValidationReport report = validate(spec);
        if (!report.isValid()) {
            throw new IllegalStateException("Invalid Stage-20B SystemSpec: " + String.join("; ", report.violations()));
        }
    }

    private static void validateIdentifiers(SystemSpec spec, List<String> violations) {
        Set<String> ids = new HashSet<>();
        addUnique(ids, spec.centralBody().id(), "central body", violations);
        for (CelestialAnchor celestial : spec.celestialAnchors()) {
            addUnique(ids, celestial.id(), "celestial", violations);
        }
        for (LocalSite site : spec.sites()) {
            addUnique(ids, site.id(), "site", violations);
        }
        spec.operationalRegions().forEach(region -> addUnique(ids, region.id(), "region", violations));
    }

    private static void validateCelestials(SystemSpec spec, List<String> violations) {
        Map<String, CelestialAnchor> byId = new HashMap<>();
        for (CelestialAnchor celestial : spec.celestialAnchors()) {
            byId.put(celestial.id(), celestial);
        }
        for (CelestialAnchor celestial : spec.celestialAnchors()) {
            LocalPhysicalPosition parentPosition;
            double parentRadius;
            if (celestial.kind() == CelestialKind.PLANET) {
                if (!celestial.parentId().equals(spec.centralBody().id())) {
                    violations.add("planet parent is not central body: " + celestial.id());
                    continue;
                }
                parentPosition = spec.centralBody().position();
                parentRadius = spec.centralBody().physicalRadiusM();
            } else {
                CelestialAnchor parent = byId.get(celestial.parentId());
                if (parent == null || parent.kind() != CelestialKind.PLANET) {
                    violations.add("moon parent is not a generated planet: " + celestial.id());
                    continue;
                }
                parentPosition = parent.position();
                parentRadius = parent.physicalRadiusM();
            }
            double actualOrbit = parentPosition.distanceTo(celestial.position());
            if (!approximatelyEqual(actualOrbit, celestial.orbitalRadiusM())) {
                violations.add("orbital radius disagrees with physical position: " + celestial.id());
            }
            double minimum = parentRadius + celestial.physicalRadiusM()
                    + spec.validationParameters().celestialClearanceM();
            if (actualOrbit < minimum) {
                violations.add("celestial body violates parent clearance: " + celestial.id());
            }
        }
    }

    private static void validateSites(SystemSpec spec, List<String> violations) {
        double clearance = spec.validationParameters().minimumSiteClearanceM();
        for (int i = 0; i < spec.sites().size(); i++) {
            LocalSite left = spec.sites().get(i);
            for (int j = i + 1; j < spec.sites().size(); j++) {
                LocalSite right = spec.sites().get(j);
                double required = left.footprintRadiusM() + right.footprintRadiusM() + clearance;
                if (left.position().distanceTo(right.position()) < required) {
                    violations.add("site overlap/clearance violation: " + left.id() + " vs " + right.id());
                }
            }
        }

        List<LocalSite> jumps = spec.sitesOfKind(SiteKind.JUMP_ZONE);
        List<LocalSite> stations = spec.sitesOfKind(SiteKind.STATION);
        for (LocalSite jump : jumps) {
            for (LocalSite station : stations) {
                double required = jump.footprintRadiusM() + station.footprintRadiusM()
                        + spec.validationParameters().jumpArrivalStationStandOffM();
                if (jump.position().distanceTo(station.position()) < required) {
                    violations.add("jump arrival violates station stand-off: " + jump.id() + " vs " + station.id());
                }
            }
        }
    }

    private static void validateExtents(SystemSpec spec, List<String> violations) {
        LocalPhysicalPosition origin = spec.centralBody().position();
        double maxInfrastructure = maxSiteExtent(spec, origin, SiteKind.STATION);
        double maxResource = maxSiteExtent(spec, origin, SiteKind.RESOURCE_FIELD);
        double maxJump = maxSiteExtent(spec, origin, SiteKind.JUMP_ZONE);
        double maxSurveyed = spec.centralBody().physicalRadiusM();
        for (CelestialAnchor celestial : spec.celestialAnchors()) {
            maxSurveyed = Math.max(maxSurveyed,
                    origin.distanceTo(celestial.position()) + celestial.physicalRadiusM());
        }
        for (LocalSite site : spec.sites()) {
            maxSurveyed = Math.max(maxSurveyed,
                    origin.distanceTo(site.position()) + site.footprintRadiusM());
        }

        if (spec.contentExtents().majorInfrastructureExtentM() + tolerance(maxInfrastructure) < maxInfrastructure) {
            violations.add("majorInfrastructureExtentM under-reports generated content");
        }
        if (spec.contentExtents().resourceFieldExtentM() + tolerance(maxResource) < maxResource) {
            violations.add("resourceFieldExtentM under-reports generated content");
        }
        if (spec.contentExtents().jumpArrivalExtentM() + tolerance(maxJump) < maxJump) {
            violations.add("jumpArrivalExtentM under-reports generated content");
        }
        if (spec.contentExtents().surveyedContentExtentM() + tolerance(maxSurveyed) < maxSurveyed) {
            violations.add("surveyedContentExtentM under-reports generated content");
        }
    }

    private static double maxSiteExtent(SystemSpec spec, LocalPhysicalPosition origin, SiteKind kind) {
        return spec.sitesOfKind(kind).stream()
                .mapToDouble(site -> origin.distanceTo(site.position()) + site.footprintRadiusM())
                .max()
                .orElse(0d);
    }

    private static void addUnique(Set<String> ids, String id, String kind, List<String> violations) {
        if (!ids.add(id)) {
            violations.add("duplicate generated id for " + kind + ": " + id);
        }
    }

    private static boolean approximatelyEqual(double left, double right) {
        return Math.abs(left - right) <= tolerance(Math.max(Math.abs(left), Math.abs(right)));
    }

    private static double tolerance(double scale) {
        return Math.max(0.01d, Math.abs(scale) * 1e-12d);
    }

    /** Immutable deterministic validation result. */
    public record ValidationReport(List<String> violations) {
        /** Defensively snapshots reported violations. */
        public ValidationReport {
            violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        }

        /** Returns true when the specification satisfies every current Stage-20B invariant. */
        public boolean isValid() {
            return violations.isEmpty();
        }
    }
}
