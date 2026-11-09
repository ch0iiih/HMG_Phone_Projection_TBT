package hmgapi.mods.projection.carplay;

/** Modern cluster display types recovered from RGIconIDToClusterDispType. */
final class FacilityGlyphs {
    private FacilityGlyphs() {}

    static int highway(int type, int junction, int exit) {
        if (junction != 0) return 0;
        switch (type) {
            case 9: return exit < 0 ? 162 : exit > 0 ? 161 : 160;
            case 52: return 162;
            case 53: return 161;
            case 8: return exit < 0 ? 165 : exit > 0 ? 164 : 163;
            case 22: return 165;
            case 23: return 164;
            default: return 0;
        }
    }

    static int road(int type, int junction, int exit, String description, String afterRoad) {
        if (junction != 0) return 0;
        String text = ((description == null ? "" : description) + " "
                + (afterRoad == null ? "" : afterRoad)).replaceAll("\\s+", "");
        if (text.contains("금지") || text.contains("진입하지") || text.contains("이용하지")) return 0;
        boolean rest = text.contains("휴게소") || text.contains("졸음쉼터");
        if (rest) {
            // Source 48/49 has two native display variants, 183 and 181.
            // 183 was physically observed as a service-area exit diagram, so try
            // the other native service-area variant. Its physical appearance is
            // still unverified; do not call it a confirmed standalone pictogram.
            if (text.contains("요금소") || text.contains("톨게이트") || text.contains("자동징수")) return 0;
            return type == 3 || type == 13 || type == 14 || highway(type, junction, exit) != 0 ? 181 : 0;
        }
        boolean under = text.contains("지하차도"), over = text.contains("고가차도");
        if (under == over) return 0;
        boolean beside = text.contains("옆길") || text.contains("측도");
        if (beside) {
            boolean left = type == 13 || exit < 0;
            boolean right = type == 14 || exit > 0;
            if (left == right) return 0;
            // Source 37/38 -> 82/81; source 42/43 -> 85/84.
            return under ? (left ? 85 : 84) : (left ? 82 : 81);
        }
        if (text.contains("진입") || text.contains("이용") || text.contains("통과") || type == 3) {
            boolean right = type == 14 || exit > 0;
            // Source 34/35/36 -> 88/88/87; source 39/40/41 -> 91/91/90.
            return under ? (right ? 90 : 91) : (right ? 87 : 88);
        }
        return 0;
    }
}
