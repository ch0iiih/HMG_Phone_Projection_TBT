package hmgapi.mods.projection.carplay;

/** Copies only current, compatible factory pocket markers; never guesses new lanes. */
public final class StockLaneHints {
    public static final String KEY = "hmgapi_carplay_stock_lane_structure";
    private StockLaneHints() {}

    public static String encode(long frameAt, long distanceAt, int distance, byte[] frame) {
        if (frame == null || frame.length != 81) return "";
        StringBuilder s = new StringBuilder().append(frameAt).append(',').append(distanceAt)
                .append(',').append(distance);
        for (byte b : frame) s.append(',').append(b & 255);
        return s.toString();
    }

    public static byte[] merge(byte[] phone, String value, long now) {
        if (phone == null || phone.length != 81 || value == null || value.length() > 800) return phone;
        String[] fields = value.split(",");
        if (fields.length != 84) return phone;
        try {
            long frameAge = now - Long.parseLong(fields[0]);
            long distanceAge = now - Long.parseLong(fields[1]);
            int distance = Integer.parseInt(fields[2]);
            if (frameAge < 0 || frameAge > 30000 || distanceAge < 0 || distanceAge > 2000
                    || distance < 0 || distance > 350) return phone;
            byte[] stock = new byte[81];
            for (int i = 0; i < stock.length; i++) {
                int n = Integer.parseInt(fields[i + 3]);
                if (n < 0 || n > 255) return phone;
                stock[i] = (byte) n;
            }
            int count = phone[1] & 255, markers = stock[2] & 255;
            if ((stock[0] & 255) != 241 || (phone[0] & 255) != 241
                    || count < 2 || count > 8 || count != (stock[1] & 255) || markers == 0) return phone;
            int left = markers >>> 4, right = markers & 15;
            if (((left | right) >>> Math.min(count, 4)) != 0) return phone;
            int common = 0;
            for (int i = 0; i < count; i++) {
                boolean pocket = (i < 4 && (left & (1 << i)) != 0)
                        || (count - 1 - i < 4 && (right & (1 << (count - 1 - i))) != 0);
                int offset = 19 + i * 3;
                // A current-road pocket with a generic straight glyph can acquire
                // the phone's left/right arrow. Other mismatches are rejected.
                boolean equal = true;
                for (int j = 0; j < 3; j++) if (phone[offset+j] != stock[offset+j]) equal = false;
                if (!equal) {
                    int nativeIcon = stock[offset] & 255, phoneIcon = phone[offset] & 255;
                    if (!pocket || nativeIcon != 1 || stock[offset+1] != 0 || stock[offset+2] != 0
                            || phoneIcon < 1 || phoneIcon > 8 || phone[offset+1] != 0 || phone[offset+2] != 0)
                        return phone;
                }
                if (!pocket) common++;
            }
            if (common == 0) return phone;
            byte[] merged = phone.clone();
            merged[2] = stock[2];
            return merged;
        } catch (NumberFormatException e) {
            return phone;
        }
    }
}
