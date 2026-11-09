package hmgapi.mods.projection.carplay;

/** Stock 0xF1 lane encoding. Each glyph part has its own two-bit possibility. */
final class LaneGlyphs {
    private LaneGlyphs() {}

    // Verified in CGuidanceControl::updateClusterLaneInfo and ParserLaneInfoJson:
    // native valid type 1/2/3 (gray/white/blue) becomes possibility 3/1/2.
    static final int GRAY = 3, WHITE = 1, BLUE = 2;

    static int[] encode(int status, int[] angles, int[] highlighted) {
        int[] result = new int[6];
        boolean[] present = new boolean[9], selected = new boolean[9];
        if (angles == null) return result;
        for (int angle : angles) {
            int icon = angleIcon(angle);
            present[icon] = true;
            if (status == 1 || status == 2) {
                if (highlighted == null) selected[icon] = true;
                else for (int h : highlighted) if (angleIcon(h) == icon) selected[icon] = true;
            }
        }
        int[] icons = new int[3];
        int count = 0;
        for (int icon = 1; icon <= 8 && count < 3; icon++) {
            if (present[icon]) icons[count++] = icon;
        }
        if (count == 2 && present[5] && present[7]) {
            // Exact same-drive stock frame at 04:43:13: left first, U-turn second.
            icons[0] = 7;
            icons[1] = 5;
        }
        int leftParts = 0, precedingParts = 0;
        for (int i = 0; i < count; i++) {
            int icon = icons[i], part = icon;
            if (count == 2 && present[1] && present[7]) part = icon == 1 ? 9 : 12;
            else if (count == 2 && present[1] && present[3]) part = icon == 1 ? 16 : 18;
            else if (count == 2 && present[5] && present[7]) {
                // Verified stock frame: left 14 provides the stem; U-turn 10 overlays it.
                part = icon == 5 ? 10 : 14;
            }
            else if (count > 2) {
                // Stock center layout for three parts; the single-arrow IDs do not overlay.
                int[] three = {0, 47, 52, 53, 54, 51, 50, 49, 48};
                part = three[icon];
            } else if (count == 2) {
                // Stock center layout, including stem variants when no straight part exists.
                switch (icon) {
                    case 1: part = 22; leftParts++; break;
                    case 2: part = precedingParts > 0 ? 23 : 33; break;
                    case 3: part = precedingParts > 0 ? 24 : 32; break;
                    case 4: part = 25; break;
                    case 5: part = 26; leftParts++; break;
                    case 6: part = 27; leftParts++; break;
                    case 7: part = leftParts > 0 ? 28 : 31; leftParts++; break;
                    case 8: part = leftParts > 1 ? 29 : 30; leftParts++; break;
                    default: break;
                }
            }
            int color = selected[icon] ? (status == 2 ? BLUE : WHITE) : GRAY;
            result[0] |= color << (i * 2);
            result[i + 1] = part;
            if (selected[icon]) {
                result[4] = 1;
                if (status == 2) result[5] = 1;
            }
            precedingParts++;
        }
        return result;
    }

    static int angleIcon(int angle) {
        int a = angle % 360;
        if (a > 180) a -= 360;
        else if (a < -180) a += 360;
        if (a >= 0) {
            if (a <= 22) return 1;
            if (a <= 67) return 2;
            if (a <= 112) return 3;
            return a <= 157 ? 4 : 5;
        }
        a = -a;
        if (a <= 22) return 1;
        if (a <= 67) return 8;
        if (a <= 112) return 7;
        return a <= 157 ? 6 : 5;
    }
}
