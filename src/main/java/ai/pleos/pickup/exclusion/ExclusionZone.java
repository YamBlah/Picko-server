package ai.pleos.pickup.exclusion;

/**
 * 주정차 금지 구역 (현재 모두 point + radius 원형).
 * 추후 polygon 지원 시 확장 가능.
 */
public record ExclusionZone(
        String source,      // fire_hydrant | crosswalk | intersection | bus_stop | school_zone | no_parking
        String name,
        double lat,
        double lng,
        double radiusM
) {}
