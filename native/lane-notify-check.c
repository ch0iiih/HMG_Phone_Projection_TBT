/*
 * Validator for the LX2 PE iAP2 lane notification representation.
 * At iAP2ControlLaneGuidanceInformation+0x84, use the packet's actual
 * written length, after checking it against the allocation length.
 */
typedef unsigned char byte;
typedef unsigned int u32;

static u32 read_le32(const byte *data) {
    return (u32)data[0] | ((u32)data[1] << 8)
        | ((u32)data[2] << 16) | ((u32)data[3] << 24);
}

static u32 validate_group(const byte **cursor, const byte *end, int nested) {
    const byte *p = *cursor;
    if (end - p < 8 || read_le32(p) == 0) return 2;
    u32 items = read_le32(p + 4);
    p += 8;
    if (items > (u32)(end - p) / 6) return 3;
    for (u32 i = 0; i < items; ++i) {
        *cursor = p;
        if (end - p < 6) return 4;
        byte type = p[1];
        u32 count = read_le32(p + 2);
        p += 6;
        switch (type) {
        case 1:
        case 5:
        case 8:
        case 9:
            if (count > (u32)(end - p) / 4) return 5;
            p += count * 4;
            break;
        case 10:
            if (count > (u32)(end - p)) return 6;
            for (u32 n = 0; n < count; ++n) {
                while (p < end && *p != 0) ++p;
                if (p == end) return 6;
                ++p;
            }
            break;
        case 13: {
            if (nested || count != 1) return 7;
            u32 error = validate_group(&p, end, 1);
            if (error) {
                *cursor = p;
                return error;
            }
            break;
        }
        default:
            return 8;
        }
    }
    *cursor = p;
    return 0;
}

/* Low byte is the reason. Upper bits give the offset of the rejected field. */
u32 lane_notify_check(const byte *bytes, u32 written, u32 allocated) {
    if (!bytes || written < 8 || written > allocated || written > 65535) return 1;
    const byte *cursor = bytes;
    const byte *end = bytes + written;
    u32 error = validate_group(&cursor, end, 0);
    if (!error && cursor != end) error = 9;
    return error ? ((u32)(cursor - bytes) << 8) | error : 0;
}

int lane_notify_valid(const byte *bytes, u32 written, u32 allocated) {
    return lane_notify_check(bytes, written, allocated) == 0;
}

/* Session packet layout at the lane notification call site (32-bit ARM). */
struct LaneNotifyPacket {
    u32 marker;
    u32 payload_capacity;
    u32 written;
    byte *bytes;
};

static u32 read_be16(const byte *data) {
    return ((u32)data[0] << 8) | data[1];
}

static void write_le32(byte *data, u32 value) {
    data[0] = (byte)value;
    data[1] = (byte)(value >> 8);
    data[2] = (byte)(value >> 16);
    data[3] = (byte)(value >> 24);
}

/* The stock serializer writes the type-13 item header before calling this.
 * Its lane group callback is empty on 260902. Append one subgroup in the
 * same representation consumed by libIap2Acc's convertSubGroup. */
int lane_append_group(void *context, struct LaneNotifyPacket *packet,
    u32 unused, const byte *wire, u32 length) {
    (void)context;
    (void)unused;
    if (!packet || !packet->bytes || packet->payload_capacity > 65531) return 0;
    u32 capacity = packet->payload_capacity + 4;
    u32 written = packet->written;
    if (written < 14 || written > capacity) return 0;
    byte *header = packet->bytes + written - 6;
    if (header[0] != 2 || header[1] != 13 || read_le32(header + 2) != 1) return 0;
    /* Mark incomplete until all fields and the destination bounds are verified. */
    header[2] = 0;
    if (!wire || length > 128 || length < 4 || capacity - written < 8) return 0;

    u32 values[4][8];
    u32 counts[4] = {0, 0, 0, 0};
    while (length >= 4) {
        u32 size = read_be16(wire);
        u32 id = read_be16(wire + 2);
        if (size < 4 || size > length) return 0;
        const byte *field = wire + 4;
        u32 bytes = size - 4;
        wire += size;
        length -= size;
        if (id > 3) continue;
        if (id == 1) {
            if (bytes != 1 || counts[1] != 0) return 0;
            values[1][counts[1]++] = field[0];
            continue;
        }
        u32 count = bytes / 2;
        if ((bytes & 1) || count > 8 - counts[id]
                || (id == 0 && (count != 1 || counts[0] != 0))) return 0;
        for (u32 i = 0; i < count; ++i) {
            u32 value = read_be16(field + 2 * i);
            if (id >= 2 && value >= 0x8000) value -= 0x10000;
            values[id][counts[id]++] = value;
        }
    }
    if (length || (!counts[1] && !counts[2] && !counts[3])) return 0;
    byte *group = packet->bytes + written;
    byte *out = group + 8;
    u32 items = 0;
    for (u32 id = 0; id < 4; ++id) {
        u32 count = counts[id];
        if (!count) continue;
        if ((u32)(packet->bytes + capacity - out) < 6 + count * 4) return 0;
        out[0] = (byte)id;
        out[1] = id == 1 ? 8 : 5;
        write_le32(out + 2, count);
        out += 6;
        for (u32 i = 0; i < count; ++i) {
            write_le32(out, values[id][i]);
            out += 4;
        }
        ++items;
    }
    write_le32(group, (u32)(out - group) - 4);
    write_le32(group + 4, items);
    packet->written = (u32)(out - packet->bytes);
    header[2] = 1;
    return 0;
}

typedef void (*LaneNotify)(void *context, int event, int arg1, int arg2,
    struct LaneNotifyPacket *packet);
typedef void (*DestroyNotifyPacket)(struct LaneNotifyPacket *packet);

/* Ownership moves to notify on success, or to destroy when dropping a packet. */
void lane_notify_checked(void *context, struct LaneNotifyPacket *packet,
    LaneNotify notify, DestroyNotifyPacket destroy) {
    if (!packet) return;
    /* The stock allocator adds a four-byte length prefix to payload_capacity. */
    if (packet->payload_capacity <= 65531
            && lane_notify_valid(packet->bytes, packet->written, packet->payload_capacity + 4)) {
        notify(context, 122, 0, 0, packet);
    } else {
        destroy(packet);
    }
}
