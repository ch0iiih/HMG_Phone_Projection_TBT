package hmgapi.mods.wired;

import hmgapi.hook.ModContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Corrects the lane callback's duplicate maneuver ID in LX2 PE 260902. */
final class LaneDispatchPatch {
    private static final String LIBRARY = "/system/lib/libiap2session.so";
    private static final String STOCK_MD5 = "fc7cb675b46a44bd893a1edc054c5e71";
    private static final String LOG_LIBRARY = "/system/lib/libIap2Acc.so";
    private static final String LOG_LIBRARY_MD5 = "fe2e69fe933cf4f3c297a89b1fe54c5a";
    private static final long LANE_ID_OFFSET = 0x30c80;
    private static final int DISPATCH_STRIDE = 24;

    private boolean finished;
    private boolean waitingLogged;

    synchronized void apply(ModContext mod) {
        if (finished) return;
        try {
            long address = mappedAddress();
            if (address < 0) return; // The library may still be loading.
            if (!STOCK_MD5.equals(ModContext.md5(LIBRARY))) {
                finished = true;
                mod.log("TBT lane dispatch skipped: unexpected library hash");
                return;
            }
            long logAddress = mappedLogger();
            try (RandomAccessFile memory = new RandomAccessFile("/proc/self/mem", "rw")) {
                long base = address - 0x31c80;
                long guard = verifiedGuard(memory, base);
                if (guard == 0 || logAddress == 0) {
                    if (!waitingLogged) mod.log("TBT lane dispatch waiting for verified guard");
                    waitingLogged = true;
                    return;
                }
                finished = true;
                if (readId(memory, address - 2 * DISPATCH_STRIDE) != 0x5201
                        || readId(memory, address - DISPATCH_STRIDE) != 0x5202
                        || readId(memory, address + DISPATCH_STRIDE) != 0x5701) {
                    mod.log("TBT lane dispatch skipped: unexpected adjacent records");
                    return;
                }
                long callback = address + 12;
                if (readId(memory, address) != 0x5202
                        || (readId(memory, callback) & 0xffffffffL) != base + 0x23639) {
                    mod.log("TBT lane dispatch skipped: unexpected lane record");
                    return;
                }
                writeWord(memory, guard + 0x88, logAddress);
                writeWord(memory, callback, guard + 0x91);
                memory.seek(address);
                memory.write(0x04);
                mod.log("TBT lane dispatch 0x5204 ready with guard and callback trace");
            }
        } catch (IOException | RuntimeException e) {
            finished = true;
            mod.error("TBT lane dispatch patch", e);
        }
    }

    private static long verifiedGuard(RandomAccessFile memory, long base) throws IOException {
        long entry = branchTarget(memory, base + 0x236bc);
        if (entry == 0) return 0;
        long guard = entry - 4; // The entry is four bytes after the marker.
        return readId(memory, guard) == 0x4c414e45
                && branchTarget(memory, base + 0x22cd0) == guard + 0x2c2
                && matchesTarget(memory, guard + 0x7c, base + 0x14525)
                && matchesTarget(memory, guard + 0x80, base + 0x19d81)
                && matchesTarget(memory, guard + 0x84, base + 0x236c1)
                && matchesTarget(memory, guard + 0x8c, base + 0x23639) ? guard : 0;
    }

    private static long branchTarget(RandomAccessFile memory, long branch) throws IOException {
        int code = readId(memory, branch);
        int high = code & 0xffff, low = code >>> 16;
        if ((high & 0xf800) != 0xf000 || (low & 0xd000) != 0x9000) return 0;
        int s = (high >>> 10) & 1;
        int i1 = 1 ^ s ^ ((low >>> 13) & 1);
        int i2 = 1 ^ s ^ ((low >>> 11) & 1);
        int offset = (s << 24) | (i1 << 23) | (i2 << 22)
                | ((high & 0x3ff) << 12) | ((low & 0x7ff) << 1);
        offset = (offset << 7) >> 7;
        return branch + 4 + offset;
    }

    private static boolean matchesTarget(RandomAccessFile memory, long slot, long expected) throws IOException {
        return ((slot + readId(memory, slot)) & 0xffffffffL) == expected;
    }

    private static int readId(RandomAccessFile memory, long address) throws IOException {
        memory.seek(address);
        return Integer.reverseBytes(memory.readInt());
    }

    private static void writeWord(RandomAccessFile memory, long address, long value) throws IOException {
        memory.seek(address);
        memory.writeInt(Integer.reverseBytes((int) value));
        if ((readId(memory, address) & 0xffffffffL) != value) {
            throw new IOException("iAP2 trace address did not persist");
        }
    }

    private static long mappedLogger() throws IOException {
        try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = maps.readLine()) != null) {
                String[] fields = line.trim().split("\\s+", 6);
                if (fields.length != 6 || !LOG_LIBRARY.equals(fields[5])
                        || !"r-xp".equals(fields[1]) || Long.parseLong(fields[2], 16) != 0) continue;
                String[] bounds = fields[0].split("-", 2);
                long start = Long.parseLong(bounds[0], 16);
                long target = start + 0x5374;
                if (target + 12 < Long.parseLong(bounds[1], 16)
                        && LOG_LIBRARY_MD5.equals(ModContext.md5(LOG_LIBRARY))) return target;
            }
        }
        return 0;
    }

    private static long mappedAddress() throws IOException {
        try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = maps.readLine()) != null) {
                String[] fields = line.trim().split("\\s+", 6);
                if (fields.length != 6 || !LIBRARY.equals(fields[5]) || !"rw-p".equals(fields[1])) continue;
                String[] bounds = fields[0].split("-", 2);
                long start = Long.parseLong(bounds[0], 16);
                long end = Long.parseLong(bounds[1], 16);
                long offset = Long.parseLong(fields[2], 16);
                long address = start + LANE_ID_OFFSET - offset;
                if (address - 2 * DISPATCH_STRIDE >= start && address + DISPATCH_STRIDE + 4 <= end) {
                    return address;
                }
            }
        }
        return -1;
    }
}
