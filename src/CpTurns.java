package hmgapi.mods.wireless.dipo;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;

import com.tcc.iap2.Iap2NotiMsg;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.LinkedHashMap;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;
import hmgapi.hook.Points;
import hmgapi.mods.wired.WiredTbt;

/**
 * CarPlay turn-by-turn on the cluster and HUD: a port of the turn-by-turn build's DiPO
 * route guidance manager (e/u, with its route update e/c and maneuver e/v), fed by
 * HMG-API's own iAP2 listener instead of DiPO's, and sending through HMG-API's Cluster API.
 * DiPO's own manager only exists when stepofspec is 999 or more, and stays off.
 *
 * The iPhone sends RouteGuidanceUpdate (120: state, distances, the current maneuver
 * list), RouteGuidanceManeuverUpdate (121: one maneuver by index) and LaneGuidance
 * (122), each for a display component, often field by field. Kept per component as in
 * DiPO. What it sends, as the build did:
 *   - the turn (updateTBTInfo: icon, distance in the phone's units), crossroad name and,
 *     when state is 6, the destination name
 *   - the HUD list (0xF2 frame: icon, next icon, road) and the HUD distance (2C)
 *   - remaining distance and arrival time (updateTBTSubInfo, in the owner's units)
 *   - up to 3 upcoming turns (updateTPTurnList, owner's units) and lanes (0xF1 frame)
 * The optional status experiment replays the legacy DiPO start and stop calls.
 * Also from the build: when the source app changes (Apple Maps to Waze, say), what the
 * old app left is cleared and route guidance restarted, so the phone resends all of it.
 * Not ported: DiPO's per-region icon filters (China, Europe, Middle East, Australia,
 * Brazil); on US and Canada cars the build sends every icon.
 * One thread: every call comes from the same handler.
 */
final class CpTurns {
    final Context context;
    final Cluster cluster;
    /** restarts the phone's route guidance updates (Stop, then Start) on a thread of its own */
    final Runnable restart;
    /** component id -> its route guidance state (DiPO e/u.Al) */
    final LinkedHashMap<Integer, Update> updates = new LinkedHashMap<Integer, Update>();
    /** component id -> maneuver index -> maneuver (DiPO e/u.Am) */
    final LinkedHashMap<Integer, LinkedHashMap<Integer, Maneuver>> maneuvers = new LinkedHashMap<Integer, LinkedHashMap<Integer, Maneuver>>();
    String source;
    /** last HUD list sent (type, next, road), so an unchanged one isn't resent */
    int hudType, hudNext;
    String hudRoad;

    CpTurns(Context context, Cluster cluster, Runnable restart) {
        this.context = context;
        this.cluster = cluster;
        this.restart = restart;
    }

    /** RouteGuidanceUpdate (DiPO e/u.c) */
    void routeGuidance(Iap2NotiMsg msg) {
        if (msg == null) return;
        sourceCheck(msg);
        int[] ids = componentIds(msg);
        if (ids == null) {
            for (Integer id : updates.keySet()) {
                Update u = updates.get(id);
                if (u != null) u.read(id, msg);
                turn(id, u);
                updateLaneDisplay(id, u);
            }
            return;
        }
        for (int k = 0; k < ids.length; k++) {
            Integer id = ids[k];
            Update u = updates.get(id);
            if (u != null) {
                u.read(id, msg);
            } else {
                u = new Update(id, msg);
                updates.put(id, u);
            }
            turn(id, u);
            updateLaneDisplay(id, u);
        }
    }

    /** the components a RouteGuidanceUpdate is for (item 0); null if it doesn't say */
    static int[] componentIds(Iap2NotiMsg msg) {
        Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
        if (g == null) return null;
        int[] ids = null;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null) return null;
            if (it.getItemParamType() == 5 && it.getItemId() == 0 && it.getItemObjCount() > 0) ids = it.getItemIntArray();
        }
        return ids;
    }

    /**
     * RouteGuidanceManeuverUpdate (DiPO e/u.d): item 0 the components, item 1 the index.
     * Stored under the index the message carries (the build's fix, by Isaac Wisdom: stock
     * wrote the index into a copy and stored every maneuver under -1, so none was found).
     */
    void maneuver(Iap2NotiMsg msg) {
        if (msg == null) return;
        int index = -1;
        int[] ids = null;
        Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
        if (g != null) {
            int[] found = null;
            boolean whole = true;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                if (it == null) {
                    whole = false;
                    break;
                }
                int id = it.getItemId();
                if (it.getItemObjCount() <= 0 || (id != 0 && id != 1)) continue;
                int[] v = it.getItemIntArray();
                if (v == null || v.length == 0) continue;
                if (id == 0) found = v;
                else index = v[0];
            }
            if (whole) ids = found;
        }
        if (index < 0) return;
        if (ids == null) {
            for (Integer id : maneuvers.keySet()) {
                LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
                if (m == null) continue;
                Maneuver x = m.get(index);
                if (x != null) x.read(index, id, msg);
                else m.put(index, new Maneuver(index, id, msg));
                retryTurn(id, index);
            }
            return;
        }
        for (int k = 0; k < ids.length; k++) {
            int id = ids[k];
            LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
            if (m != null) {
                Maneuver x = m.get(index);
                if (x != null) x.read(index, id, msg);
                else m.put(index, new Maneuver(index, id, msg));
            } else {
                m = new LinkedHashMap<Integer, Maneuver>();
                m.put(index, new Maneuver(index, id, msg));
                maneuvers.put(id, m);
            }
            retryTurn(id, index);
        }
    }

    private void retryTurn(int id, int index) {
        Update u = updates.get(id);
        if (u == null || u.state <= 0 || u.list == null) return;
        if (u.list.length == 1 && index == adjacentNext(u)) {
            turn(id, u);
            return;
        }
        for (int listed : u.list) {
            if (listed == index) {
                turn(id, u);
                return;
            }
        }
    }

    /** Naver sends contiguous maneuver IDs in each route snapshot. */
    private int adjacentNext(Update u) {
        if (!"네이버지도".equals(source) || u.state != 1 || u.list.length != 1) return -1;
        int next = u.list[0] + 1;
        return next > 0 && next < u.listCountAtSelection ? next : -1;
    }

    private static final class LaneFrame {
        final int component, index;
        final byte[] bytes;
        final long receivedAt = SystemClock.elapsedRealtime();

        LaneFrame(int component, int index, byte[] bytes) {
            this.component = component;
            this.index = index;
            this.bytes = bytes;
        }
    }

    private LaneFrame pendingLanes, activeLanes;
    private long lastLaneSentAt;
    private final Runnable expireLanes = new Runnable() {
        @Override
        public void run() {
            clearLaneDisplay();
        }
    };

    private void setLaneLease(long until) {
        try {
            Settings.Global.putLong(context.getContentResolver(), WiredTbt.LANE_LEASE, until);
        } catch (RuntimeException e) {
            Dipo.error("CarPlay lane lease", e);
        }
    }

    private void clearLaneDisplay() {
        if (activeLanes == null) return;
        Dipo.turnsHandler.removeCallbacks(expireLanes);
        byte[] empty = new byte[81];
        empty[0] = (byte) 0xF1;
        cluster.laneInfo(empty);
        setLaneLease(0);
        Dipo.log("Wired CarPlay: lane display released index=" + activeLanes.index);
        activeLanes = null;
        lastLaneSentAt = 0;
    }

    private void updateLaneDisplay(int component, Update route) {
        if (route == null) return;
        boolean visible = route.state > 0 && route.lanesShown;
        if (activeLanes != null && activeLanes.component == component
                && (!visible || activeLanes.index != route.laneIndex)) clearLaneDisplay();
        LaneFrame pending = pendingLanes;
        long now = SystemClock.elapsedRealtime();
        if (pending != null && now - pending.receivedAt > 2000) {
            pendingLanes = null;
        } else if (pending != null && pending.component == component && pending.index == route.laneIndex && visible) {
            activeLanes = pending;
            pendingLanes = null;
            lastLaneSentAt = 0;
            Dipo.log("Wired CarPlay: lane display active index=" + pending.index);
        }
        if (activeLanes != null && activeLanes.component == component
                && activeLanes.index == route.laneIndex && visible && now - lastLaneSentAt >= 1000) {
            setLaneLease(System.currentTimeMillis() + WiredTbt.LANE_LEASE_MS);
            cluster.laneInfo(activeLanes.bytes);
            Dipo.turnsHandler.removeCallbacks(expireLanes);
            Dipo.turnsHandler.postDelayed(expireLanes, WiredTbt.LANE_LEASE_MS);
            lastLaneSentAt = now;
        }
    }

    /** LaneGuidance (the build's applyLanes): up to 8 lanes as the cluster's 0xF1 frame */
    void lanes(Iap2NotiMsg msg) {
        Iap2NotiMsg.Iap2ObjGroup g = msg == null ? null : msg.getItemGroup();
        if (g == null) return;
        int[] components = componentIds(msg);
        if (components == null || components.length == 0) return;
        int laneIndex = -1;
        int[] status = new int[8], a = new int[8], b = new int[8], c = new int[8];
        int possibleMask = 0, preferredMask = 0;
        int n = 0;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it != null && it.getItemParamType() == 5 && it.getItemId() == 1) {
                int[] values = it.getItemIntArray();
                if (values != null && values.length > 0) laneIndex = values[0];
            }
            if (n == 8 || it == null || it.getItemParamType() != 13 || it.getItemId() != 2) continue;
            Iap2NotiMsg.Iap2ObjGroup[] lanes = it.getItemGroupArray();
            if (lanes == null) continue;
            for (Iap2NotiMsg.Iap2ObjGroup lane : lanes) {
                if (lane == null) continue;
                int[] one = laneOne(lane);
                status[n] = one[0];
                a[n] = one[1];
                b[n] = one[2];
                c[n] = one[3];
                if (one[4] != 0) possibleMask |= 1 << n;
                if (one[5] != 0) preferredMask |= 1 << n;
                n++;
                if (n == 8) break;
            }
        }
        byte[] f = new byte[81];
        f[0] = (byte) 0xF1;
        f[1] = (byte) n;
        for (int k = 0; k < n; k++) {
            f[k + 3] = (byte) status[k];
            f[k * 3 + 19] = (byte) a[k];
            f[k * 3 + 20] = (byte) b[k];
            f[k * 3 + 21] = (byte) c[k];
        }
        f[75] = (byte) possibleMask;
        f[77] = (byte) preferredMask;
        int passable = Integer.bitCount(possibleMask);
        Dipo.log("Wired CarPlay: lane frame index=" + laneIndex
                + " lanes=" + n + " passable=" + passable
                + " statuses=" + java.util.Arrays.toString(java.util.Arrays.copyOf(status, n)));
        if (laneIndex < 0) return;
        int component = components[0];
        if (passable == 0) {
            if (activeLanes != null && activeLanes.component == component
                    && activeLanes.index == laneIndex) clearLaneDisplay();
            if (pendingLanes != null && pendingLanes.component == component
                    && pendingLanes.index == laneIndex) pendingLanes = null;
            return;
        }
        pendingLanes = new LaneFrame(component, laneIndex, f);
        updateLaneDisplay(component, updates.get(component));
    }

    /** Packed two-bit styles per arrow (1 gray, 2 preferred, 3 white), three icons, usable/preferred flags. */
    static int[] laneOne(Iap2NotiMsg.Iap2ObjGroup g) {
        int[] r = new int[6];
        if (g == null) return r;
        int receivedStatus = -1;
        int[] angles = null, highlight = null;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null) continue;
            int t = it.getItemParamType();
            if (t != 5 && t != 8) continue;
            int[] v = it.getItemIntArray();
            if (v == null || v.length == 0) continue;
            int id = it.getItemId();
            if (id == 1) {
                receivedStatus = v[0];
            } else if (id == 2) {
                angles = v;
            } else if (id == 3) {
                highlight = v;
            }
        }
        if (angles != null) {
            int n = 0;
            for (int k = 0; k < angles.length && n < 3; k++) {
                int icon = laneIcon(angles[k]);
                if (icon == 0) continue;
                boolean usable = receivedStatus != 0 && (receivedStatus == 1 || receivedStatus == 2
                        || receivedStatus < 0);
                boolean selected = highlight == null && angles.length == 1
                        && (receivedStatus == 1 || receivedStatus == 2);
                if (highlight != null) {
                    for (int angle : highlight) if (angle == angles[k]) selected = true;
                }
                int style = 1;
                if (usable && selected) {
                    style = receivedStatus == 2 ? 2 : 3;
                    r[4] = 1;
                    if (receivedStatus == 2) r[5] = 1;
                }
                r[0] |= style << (2 * n);
                r[1 + n++] = icon;
            }
        }
        Dipo.log("Wired CarPlay: lane detail inputStatus=" + receivedStatus
                + " angles=" + java.util.Arrays.toString(angles)
                + " highlightedAngles=" + java.util.Arrays.toString(highlight)
                + " outputStyles=" + r[0]);
        return r;
    }

    /** a lane angle (degrees, right positive) as the cluster's arrow: 1 straight, 2-4 right, 5 back, 6-8 left */
    static int laneIcon(int a) {
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

    private Object legacyCluster;
    private Method legacyAlive, legacyOperation;
    private boolean legacyActive, legacyUnavailable;

    private void startLegacyStatus() {
        if (legacyActive || legacyUnavailable || !Dipo.mod.settingBoolean("cluster_status_trial", false)) return;
        try {
            legacyCluster = Points.newInstance(null, "framework.navigation_cluster.new", context);
            legacyAlive = legacyCluster.getClass().getMethod("responseNaviAliveStatus", boolean.class);
            legacyOperation = legacyCluster.getClass().getMethod("updateOperationStatus", int.class);
            legacyActive = true;
            legacyAlive.invoke(legacyCluster, true);
            legacyOperation.invoke(legacyCluster, 1);
            Dipo.log("Wired CarPlay: legacy cluster status started");
        } catch (Exception e) {
            Dipo.error("Wired CarPlay: legacy cluster start", e);
            stopLegacyStatus();
            legacyUnavailable = true;
        }
    }

    private void stopLegacyStatus() {
        if (!legacyActive) return;
        boolean restored = true;
        try {
            legacyAlive.invoke(legacyCluster, false);
        } catch (Exception e) {
            restored = false;
            Dipo.error("Wired CarPlay: legacy cluster alive stop", e);
        }
        try {
            legacyOperation.invoke(legacyCluster, 0);
        } catch (Exception e) {
            restored = false;
            Dipo.error("Wired CarPlay: legacy cluster operation stop", e);
        }
        if (restored) {
            legacyActive = false;
            Dipo.log("Wired CarPlay: legacy cluster status stopped");
        }
    }

    // Route setup/recalculation can briefly report inactive before the next active frame.
    private boolean showingGuidance;
    private String lastTurnTrace;
    private boolean tripRefreshLogged;
    private final Runnable clearInactiveRoute = new Runnable() {
        @Override
        public void run() {
            stop("confirmed inactive");
        }
    };

    /** The iAP2 session ended. */
    void disconnected() {
        stop("iAP2 disconnected");
        source = null;
    }

    void stop(String reason) {
        Dipo.turnsHandler.removeCallbacks(clearInactiveRoute);
        pendingLanes = null;
        clearLaneDisplay();
        lastTrip = null;
        lastAheadDistance = null;
        setTurnLease(0);
        tripOutput = null;
        if (distanceLeaseRenewAt != 0) {
            setDistanceLease(0);
            distanceLeaseRenewAt = 0;
        }
        tripRefreshLogged = false;
        lastTurnTrace = null;
        Dipo.turnsHandler.removeCallbacks(refreshTrip);
        stopLegacyStatus();
        reset();
        if (!showingGuidance) return;
        showingGuidance = false;
        // The LX2 PE native navigation ends its display sequence with TBT type 8.
        cluster.destinationName(0, "");
        cluster.tbtInfo(8, 0, 0f, 0f, -1, 0, 0, 0, -1);
        cluster.tbtSubInfo(0f, -1, 0, 0, 0, 0, 0, 0f, -1, 0);
        hudBlank();
        outputState("clear sent type=8 (" + reason + ")");
    }

    /** forget every component's state and maneuvers (DiPO e/u.nT) */
    void reset() {
        for (LinkedHashMap<Integer, Maneuver> m : maneuvers.values()) {
            if (m != null) m.clear();
        }
        for (Update u : updates.values()) {
            if (u != null) u.clear();
        }
    }

    /** SourceName (item 19) changed: clear the old app's leftovers and have the phone resend */
    void sourceCheck(Iap2NotiMsg msg) {
        Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
        if (g == null) return;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null || it.getItemId() != 19 || it.getItemParamType() != 10 || it.getItemObjCount() <= 0) continue;
            String[] v = it.getItemStringArray();
            String s = v == null || v.length == 0 ? null : v[0];
            if (s == null || s.length() == 0) continue;
            if ("Apple 지도".equals(s)) s = "Apple Maps";
            String was = source;
            source = s;
            if (was == null) {
                Dipo.log("Wired CarPlay: guidance source=" + s);
                return;
            }
            if (was.equals(s)) return;
            Dipo.log("CarPlay: route guidance source changed from " + was + " to " + s + ", clearing turn-by-turn");
            stop("guidance source changed");
            if (restart != null) new Thread(restart, "hmgapi-cp-rg-restart").start();
            return;
        }
    }

    private String lastOutputState;
    private void outputState(String state) {
        if (state.equals(lastOutputState)) return;
        lastOutputState = state;
        Dipo.log("Wired CarPlay: cluster path " + state);
    }

    /** one component's update to the cluster (DiPO e/u.a(Integer, Object), with the build's changes) */
    void turn(Integer id, Update u) {
        if (id < 0 || u == null) return;
        if (u.state < 0) {
            outputState("waiting for route state component=" + id);
            return;
        }
        if (u.state == 0) {
            outputState("inactive component=" + id + " state=" + u.state +
                    " turnCount=" + (u.list == null ? 0 : u.list.length));
            Dipo.turnsHandler.removeCallbacks(clearInactiveRoute);
            Dipo.turnsHandler.postDelayed(clearInactiveRoute, 500);
            return;
        }
        Dipo.turnsHandler.removeCallbacks(clearInactiveRoute);
        int[] list = u.list == null ? null : u.list.clone();
        float dist = 0f;
        if (u.nextStr != null && !u.nextStr.equals("")) {
            try {
                dist = Float.parseFloat(u.nextStr);
                if (Float.isNaN(dist) || Float.isInfinite(dist)) dist = 0f;
            } catch (NumberFormatException e) {
                dist = 0f;
            }
        }
        showingGuidance = true;
        if (u.state == 6) cluster.destinationName(0, u.destination);
        sendTrip(u);
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        if (list == null || list.length == 0) {
            outputState("waiting for turn list");
            return;
        }
        Maneuver x = m == null ? null : m.get(list[0]);
        if (x == null) {
            outputState("waiting for maneuver");
            return;
        }
        startLegacyStatus();
        int unit;
        switch (u.nextUnits) {
            case 0: unit = Cluster.UNIT_KM; break;
            case 1: unit = Cluster.UNIT_MI; break;
            case 3: unit = Cluster.UNIT_YD; break;
            case 4: unit = Cluster.UNIT_FT; break;
            default: unit = Cluster.UNIT_M; break;
        }
        int icon = maneuverIcon(x, u.state);
        int adjacent = adjacentNext(u);
        if (adjacent >= 0 && x.type != 10 && x.type != 12 && x.type != 27 && m.containsKey(adjacent)) {
            list = new int[] { list[0], adjacent };
        }
        Maneuver next = list.length > 1 ? m.get(list[1]) : null;
        String trace = "index=" + list[0] + " type=" + x.type + " side=" + x.side + " junction=" + x.junction
                + " exit=" + (x.hasExit ? String.valueOf(x.exit) : "missing") + " icon=" + icon
                + " selectedList=" + java.util.Arrays.toString(list) + " count=" + list.length
                + x.facilityTrace() + " next=" + (next == null ? "missing"
                        : next.type + "/" + next.exit + "/" + next.distance + next.facilityTrace());
        if (!trace.equals(lastTurnTrace)) {
            Dipo.log("Wired CarPlay 0.1.41 turn " + trace);
            lastTurnTrace = trace;
        }
        cluster.crossroadName(x.displayRoad());
        if (icon == 0) {
            outputState("no icon for maneuver type " + x.type + " junction=" + x.junction
                    + " side=" + x.side + " exit=" + x.exit);
            return;
        }
        outputState("sending turn to NavigationCluster");
        setTurnLease(System.currentTimeMillis() + 2500);
        cluster.tbtInfo(icon, 0, 0f, dist, 0, 0, unit, 0, 0);
        sendHud(icon, id, list);
        sendHudDist(dist, unit, next);
        sendAhead(id, list);
    }

    private static int maneuverIcon(Maneuver x, int state) {
        int facility = facilityIcon(x);
        if (facility != 0) return facility;
        int icon = icon(x.type, x.side, x.junction, x.exit, state);
        if (icon != 0 || !x.hasExit || x.junction != 1 || x.side != 0) return icon;
        // Some apps describe the roundabout with the junction and angle alone.
        if (!(x.type == 0 || x.type == 1 || x.type == 2 || x.type == 6 || x.type == 7
                || x.type == 19 || (x.type >= 28 && x.type <= 46))) return 0;
        int angle = x.exit;
        if (angle < -180 || angle > 180) return 0;
        if (Math.abs(angle) >= 158) return 102;
        if (angle >= 113) return 100;
        if (angle >= 68) return 99;
        if (angle >= 23) return 97;
        if (angle >= -22) return 96;
        if (angle >= -67) return 106;
        if (angle >= -112) return 105;
        return 103;
    }

    private static int facilityIcon(Maneuver x) {
        if (x.junction != 0 || (x.type != 3 && x.type != 13 && x.type != 14)) return 0;
        if (x.tollText()) return x.restText() ? 0 : 178;
        return x.roadFacilityIcon();
    }

    /**
     * The cluster icon for a maneuver: iAP2 ManeuverType, driving side (0 right-hand
     * traffic, 1 left), junction type (0 intersection, 1 roundabout) and exit angle, as
     * the turn-by-turn build maps them (0: nothing to show).
     */
    static int icon(int type, int side, int junction, int exit, int state) {
        switch (type) {
            case 20:  // left at end of road
            case 1:   // left turn
                if (junction == 0 && exit == -120) return 69;
                if (side == 0 && junction == 0 && exit == -90) return 70;
                if (junction == 0 && exit == -60) return 71;
                if (junction == 0 && exit == -45) return 145;
                if (side == 1 && junction == 1 && exit == -120) return 104;
                if (side == 1 && junction == 1 && exit == -90) return 105;
                if (side == 1 && junction == 1 && exit == -30) return 107;
                return junction == 0 ? 70 : 0;
            case 21:  // right at end of road
            case 2:   // right turn
                if (junction == 0 && exit == 60) return 66;
                if (side == 0 && junction == 0 && exit == 90) return 67;
                if (junction == 0 && exit == 120) return 68;
                if (side == 0 && junction == 1 && exit == 0) return 96;
                if (side == 1 && junction == 1 && exit == 0) return 97;
                if (side == 1 && junction == 1 && exit == 90) return 99;
                if (side == 1 && junction == 1 && exit == 120) return 100;
                if (side == 1 && junction == 1 && exit == 180) return 102;
                if (side == 0 && junction == 0 && exit == 45) return 144;
                return junction == 0 ? 67 : 0;
            case 3:   // straight
                return junction == 0 ? 65 : 0;
            case 13:  // keep left
                return junction == 0 ? 71 : 0;
            case 14:  // keep right
                return junction == 0 ? 66 : 0;
            case 18:  // U-turn to rejoin route
            case 26:  // U-turn when possible
            case 4:   // U-turn
                if (side == 0 && junction == 0) return 72;
                if (side == 1 && junction == 0) return 73;
                return 0;
            case 5:
                return side == 0 && junction == 0 && exit == 0 ? 172 : 0;
            case 9: // highway on-ramp
                if (junction != 0) return 0;
                if (exit < 0) return 71;
                if (exit > 0) return 173;
                return 172;
            case 8: // highway off-ramp without a side-specific maneuver type
                if (junction != 0) return 0;
                if (exit < 0) return 71;
                if (exit > 0) return 164;
                return 86;
            case 10: // end of navigation
            case 27: // end of directions
                return state == 2 ? 3 : 0;
            case 12: {   // arrive; state 2 (arrived): 3
                int i = side == 0 && junction == 0 ? 112 : 0;
                return state == 2 ? 3 : i;
            }
            case 15: return side == 0 && junction == 0 && exit == 0 ? 176 : 0;
            case 19: return side == 0 && junction == 1 && exit == -180 ? 102 : 0;
            case 22: return junction == 0 ? 71 : 0;
            case 23: return junction == 0 ? 164 : 0;
            case 24: return side == 0 && junction == 0 && exit == 0 ? 114 : 0;
            case 25: return side == 0 && junction == 0 && exit == 0 ? 113 : 0;
            case 28: return side == 0 && junction == 1 && exit == 135 ? 100 : 0;
            case 29: return side == 0 && junction == 1 && exit == 90 ? 99 : 0;
            case 31:
                if (side == 0 && junction == 1 && exit == 0) return 97;
                if (side == 1 && junction == 1 && exit == 0) return 96;
                return 0;
            case 32: return side == 0 && junction == 1 && exit == -45 ? 104 : 0;
            case 33: return side == 0 && junction == 1 && exit == -90 ? 105 : 0;
            case 34: return side == 0 && junction == 1 && exit == -135 ? 104 : 0;
            case 35: return side == 0 && junction == 1 && exit == -180 ? 102 : 0;
            case 47:
                if (junction == 0 && exit <= -135 && exit >= -150) return 69;
                if (junction == 0 && exit == -30) return 71;
                return junction == 0 ? 69 : 0;
            case 48:
                if (junction == 0 && exit == 30) return 66;
                if (junction == 0 && exit >= 135 && exit <= 150) return 68;
                return junction == 0 ? 68 : 0;
            case 49: return junction == 0 ? 71 : 0;
            case 50: return junction == 0 ? 66 : 0;
            case 52: return side == 0 && junction == 0 && exit == 0 ? 71 : 0;
            case 53: return side == 0 && junction == 0 && exit == 0 ? 173 : 0;
            default: return 0;
        }
    }

    /** an upcoming turn's small icon (HUD list and turn list): 64 none, 65 straight, 66-68 right, 69-71 left, 72/73 U-turn */
    static int aheadIcon(Maneuver x) {
        int facility = facilityIcon(x);
        if (facility != 0) return facility;
        if (x.junction == 1) {
            int roundabout = maneuverIcon(x, 1);
            if (roundabout >= 96 && roundabout <= 107) return roundabout;
        }
        int type = x.type, side = x.side, junction = x.junction, exit = x.exit;
        if (type == 1) {
            if (exit == -120 && junction == 0) return 69;
            if (exit == -90 && side == 0 && junction == 0) return 70;
            if (exit == -60 && junction == 0) return 71;
            return 70;
        }
        if (type == 2) {
            if (exit == 60 && junction == 0) return 66;
            if (exit == 90 && side == 0 && junction == 0) return 67;
            if (exit == 120 && junction == 0) return 68;
            return 67;
        }
        if (type == 3) return 65;
        if (type == 4 || type == 18 || type == 26) return side == 0 ? 72 : 73;
        if (junction == 0 && type == 13) return 71;
        if (junction == 0 && type == 14) return 66;
        if (type == 8 || type == 9 || type == 22 || type == 23 || type == 52 || type == 53) {
            int ramp = icon(type, side, junction, exit, 1);
            if (ramp != 0) return ramp;
        }
        return 64;
    }

    /** the HUD's list frame (0xF2): count, this icon, the next turn's icon, 0, road after this turn (UTF-16LE) */
    void sendHud(int type, Integer id, int[] list) {
        if (type < 64) return;
        int next = 0;
        String road = "";
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        if (m != null && list != null && list.length > 0) {
            Maneuver x = m.get(list[0]);
            if (x != null) road = x.displayRoad();
            if (list.length > 1) {
                Maneuver y = m.get(list[1]);
                if (y != null) {
                    int nextIcon = aheadIcon(y);
                    if (nextIcon != 64) next = nextIcon;
                }
            }
        }
        if (hudType == type && hudNext == next && hudRoad != null && hudRoad.equals(road)) return;
        byte[] r;
        try {
            r = road.getBytes("UTF-16LE");
        } catch (java.io.UnsupportedEncodingException e) {
            return;
        }
        int n = Math.min(r.length, 78);
        byte[] f = new byte[n + 5];
        f[0] = (byte) 0xF2;
        f[1] = (byte) (next != 0 ? 2 : 1);
        f[2] = (byte) type;
        f[3] = (byte) next;
        System.arraycopy(r, 0, f, 5, n);
        Dipo.log("CarPlay: hud list type " + type + " next " + next);
        cluster.turnListNew(f);
        hudType = type;
        hudNext = next;
        hudRoad = road;
    }

    /** the HUD's distance (updateTBTSubInfo2C: unit, tenths for km/mi, whole up to 4095) */
    void sendHudDist(float f, int unit, Maneuver next) {
        if (f < 0f || unit < 0 || unit > 4) return;
        int whole = (int) f;
        if (whole > 4095) whole = 4095;
        int tenths = 0;
        if (unit == Cluster.UNIT_KM || unit == Cluster.UNIT_MI) {
            tenths = (int) ((f - whole) * 10f + 0.5f);
            if (tenths < 0) tenths = 0;
            if (tenths > 9) tenths = 9;
        }
        int nextUnit = 15, nextTenths = 15, nextWhole = 65535;
        if (hudNext != 0 && next != null && next.distance > 0) {
            int meters = next.distance;
            nextUnit = meters < 1000 ? Cluster.UNIT_M : Cluster.UNIT_KM;
            nextTenths = meters < 1000 ? 0 : (meters % 1000) / 100;
            nextWhole = meters < 1000 ? meters : Math.min(4095, meters / 1000);
        }
        cluster.tbtSubInfo2C(unit, tenths, whole, nextUnit, nextTenths, nextWhole, 15, 15, 65535);
    }

    /** HUD list and extras cleared (the build's sendHudBlank) */
    void hudBlank() {
        cluster.tbtSubInfo2C(0, 0, 0, 0, 0, 0, 0, 0, 0);
        byte[] lanes = new byte[81];
        lanes[0] = (byte) 0xF1;
        cluster.laneInfo(lanes);
        hudType = -1;
        hudNext = 0;
        hudRoad = null;
        byte[] empty = { (byte) 0xF2 };
        cluster.turnListNew(empty);
        cluster.turnListNew(empty);
    }

    /**
     * Remaining distance and arrival time (updateTBTSubInfo). Distance in the owner's
     * units (Settings), else the phone's. Arrival: a clock time (seconds since 2001 or
     * 1970) or, failing that, now + the time remaining (up to a week).
     */
    private String lastTrip;
    private Runnable tripOutput;
    private long distanceLeaseRenewAt;

    private void setTurnLease(long until) {
        try {
            Settings.Global.putLong(context.getContentResolver(),
                    "hmgapi_lx2pe_carplay_turn_until", until);
        } catch (RuntimeException e) {
            Dipo.error("CarPlay turn lease", e);
        }
    }

    private void setDistanceLease(long expiresAt) {
        try {
            if (!Settings.Global.putLong(context.getContentResolver(),
                    "hmgapi_lx2pe_carplay_distance_until", expiresAt)) {
                Dipo.log("Wired CarPlay: distance lease write failed");
            }
        } catch (RuntimeException e) {
            Dipo.error("Wired CarPlay: distance lease write", e);
        }
    }

    private void renewDistanceLease() {
        long now = SystemClock.elapsedRealtime();
        if (now < distanceLeaseRenewAt) return;
        distanceLeaseRenewAt = now + 3000;
        setDistanceLease(System.currentTimeMillis() + 5000);
    }
    private final Runnable refreshTrip = new Runnable() {
        @Override
        public void run() {
            if (!showingGuidance || tripOutput == null) return;
            tripOutput.run();
            if (!tripRefreshLogged) {
                Dipo.log("Wired CarPlay 0.1.41 trip refresh running");
                tripRefreshLogged = true;
            }
            Dipo.turnsHandler.postDelayed(this, 750);
        }
    };

    void sendTrip(Update u) {
        int d = u.distLeft, units = u.distLeftUnits;
        int whole = 0, tenths = 0, unit = Cluster.UNIT_M;
        if (d > 0) {
            whole = d;
            int owner = Car.distanceUnit(context);
            if (owner == Car.DISTANCE_KM) units = d < 1000 ? 2 : 0;
            else if (owner == Car.DISTANCE_MILES) units = d < 161 ? 4 : 1;
            switch (units) {
                case 0:
                    whole = d / 1000;
                    tenths = (d % 1000) / 100;
                    unit = Cluster.UNIT_KM;
                    break;
                case 1: {
                    int t = d * 10 / 1609;
                    tenths = t % 10;
                    whole = t / 10;
                    unit = Cluster.UNIT_MI;
                    break;
                }
                case 2:
                    break;
                case 3:
                    whole = d * 1094 / 1000;
                    unit = Cluster.UNIT_YD;
                    break;
                case 4:
                    whole = d * 3281 / 1000;
                    unit = Cluster.UNIT_FT;
                    break;
                default:
                    if (d >= 1000) {
                        whole = d / 1000;
                        tenths = (d % 1000) / 100;
                        unit = Cluster.UNIT_KM;
                    }
                    break;
            }
        }
        int hour = -1, minute = -1, timeType = -1, timeFormat;
        Calendar cal = null;
        long eta = u.eta;
        if (eta < 500000000L) {
            long left = u.timeLeft;
            if (left > 0 && left <= 604800L) {
                cal = Calendar.getInstance();
                cal.add(Calendar.SECOND, (int) left);
            }
        } else {
            if (eta < 1500000000L) eta += 978307200L;   // Apple's 2001 epoch
            cal = Calendar.getInstance();
            cal.setTimeInMillis(eta * 1000L);
        }
        if (cal != null) {
            hour = cal.get(Calendar.HOUR_OF_DAY);
            minute = cal.get(Calendar.MINUTE);
            timeType = 1;
            timeFormat = context == null || DateFormat.is24HourFormat(context) ? 2 : 1;
        } else {
            timeFormat = -1;
        }
        if (d <= 0) return;
        String trip = whole + ":" + tenths + ":" + (d > 0) + ":" + unit + ":" +
                hour + ":" + minute + ":" + timeType + ":" + timeFormat;
        if (trip.equals(lastTrip)) return;
        lastTrip = trip;
        final float distance = whole + tenths / 10f;
        final int distanceUnit = unit, arrivalHour = hour, arrivalMinute = minute;
        final int arrivalType = timeType, arrivalFormat = timeFormat;
        tripOutput = new Runnable() {
            @Override
            public void run() {
                renewDistanceLease();
                cluster.tbtSubInfo(distance, 0, distanceUnit, arrivalHour, arrivalMinute,
                        arrivalType, arrivalFormat, 0f, -1, 0);
            }
        };
        Dipo.log("Wired CarPlay 0.1.41 trip distance=" + distance + " distanceErr=0 unit=" + distanceUnit);
        tripOutput.run();
        Dipo.turnsHandler.removeCallbacks(refreshTrip);
        Dipo.turnsHandler.postDelayed(refreshTrip, 750);
    }

    private String lastAheadDistance;

    /** up to 3 upcoming turns after this one (updateTPTurnList), distances in the owner's units */
    void sendAhead(Integer id, int[] list) {
        if (id == null || list == null || list.length <= 1) return;
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        if (m == null) return;
        int count = 0;
        for (int k = 1; k < list.length && count < 3; k++) {
            Maneuver x = m.get(list[k]);
            if (x != null && x.distance > 0) count++;
        }
        if (count == 0) return;
        int owner = Car.distanceUnit(context);
        Cluster.Turn[] turns = new Cluster.Turn[count];
        int n = 0, firstIndex = -1;
        for (int k = 1; k < list.length && n < 3; k++) {
            Maneuver x = m.get(list[k]);
            if (x == null || x.distance <= 0 || n >= count) continue;
            Cluster.Turn t = new Cluster.Turn();
            t.type = aheadIcon(x);
            t.direction = 0;
            t.traffic = 0;
            t.no = n + 1;
            t.name = x.displayRoad();
            t.exitNumber = "";
            int d = x.distance;
            float f = d;
            int unit = Cluster.UNIT_M;
            if (owner == Car.DISTANCE_KM) {
                if (d >= 1000) {
                    unit = Cluster.UNIT_KM;
                    f = d / 1000f;
                }
            } else if (owner == Car.DISTANCE_MILES) {
                if (d < 161) {
                    unit = Cluster.UNIT_FT;
                    f = d * 3.28084f;
                } else {
                    unit = Cluster.UNIT_MI;
                    f = d / 1609f;
                }
            }
            if (f < 0f) f = 0f;
            if (f > 65535f) f = 65535f;
            t.distance = f;
            t.unit = unit;
            if (n == 0) firstIndex = list[k];
            turns[n++] = t;
        }
        if (n > 0) {
            Cluster.Turn first = turns[0];
            String distance = "current=" + list[0] + " next=" + firstIndex + " distance="
                    + first.distance + " unit=" + first.unit + " position=" + first.no;
            if (!distance.equals(lastAheadDistance)) {
                Dipo.log("TBT ahead distance " + distance);
                lastAheadDistance = distance;
            }
            cluster.turnList(turns);
        }
    }

    /** one display component's route guidance state (DiPO e/c) */
    static final class Update {
        int state = -1, maneuverState, distLeft, distLeftUnits, distNext, nextUnits, count, laneIndex, laneCount;
        String road, destination, distLeftStr, nextStr, sourceName;
        long eta, timeLeft;
        int[] list;
        int listCountAtSelection;
        boolean visible, lanesShown, supports;

        /** as DiPO: a component id of 0 or less starts with nothing set (not even the defaults) */
        Update(int id, Iap2NotiMsg msg) {
            if (msg == null || id <= 0) return;
            clear();
            read(id, msg);
        }

        void clear() {
            state = -1;
            maneuverState = 1;
            road = "";
            destination = "";
            eta = 0;
            timeLeft = 0;
            distLeft = 0;
            distLeftStr = "";
            distLeftUnits = 0;
            distNext = 0;
            nextStr = "";
            nextUnits = 0;
            list = null;
            listCountAtSelection = 0;
            count = 0;
            visible = false;
            laneIndex = 0;
            laneCount = 0;
            lanesShown = false;
            sourceName = "";
            supports = false;
        }

        /** the fields this message carries (by iAP2 type: 1/5 uint8/16, 2/6 uint32, 3/7 uint64, 8 enum, 9 bool, 10 string) */
        void read(int id, Iap2NotiMsg msg) {
            Iap2NotiMsg.Iap2ObjGroup g = msg == null ? null : msg.getItemGroup();
            if (g == null) return;
            int[] previousList = list;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                int item = it.getItemId(), n = it.getItemObjCount();
                switch (it.getItemParamType()) {
                    case 1:
                    case 5: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        switch (item) {
                            case 13:
                                if (v != null) {
                                    list = new int[n];
                                    for (int j = 0; j < n; j++) list[j] = v[j];
                                } else {
                                    list = null;
                                }
                                break;
                            case 14: count = v != null ? v[0] : 0; break;
                            case 16: laneIndex = v != null ? v[0] : 0; break;
                            case 17: laneCount = v != null ? v[0] : 0; break;
                        }
                        break;
                    }
                    case 2:
                    case 6: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (v == null) break;
                        if (item == 7) distLeft = v[0];
                        else if (item == 10) distNext = v[0];
                        break;
                    }
                    case 3:
                    case 7: {
                        long[] v = n > 0 ? it.getItemLongArray() : null;
                        if (v == null) break;
                        if (item == 5) eta = v[0];
                        else if (item == 6) timeLeft = v[0];
                        break;
                    }
                    case 8: {
                        int[] v = it.getItemIntArray();
                        if (v == null || v.length == 0) break;
                        switch (item) {
                            case 1: state = v[0]; break;
                            case 2: maneuverState = v[0]; break;
                            case 9: distLeftUnits = v[0]; break;
                            case 12: nextUnits = v[0]; break;
                        }
                        break;
                    }
                    case 9: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (v == null) break;
                        if (item == 15) visible = v[0] != 0;
                        else if (item == 18) lanesShown = v[0] != 0;
                        else if (item == 20) supports = v[0] != 0;
                        break;
                    }
                    case 10: {
                        String[] v = n > 0 ? it.getItemStringArray() : null;
                        if (v == null) break;
                        switch (item) {
                            case 3: road = v[0]; break;
                            case 4: destination = v[0]; break;
                            case 8: distLeftStr = v[0]; break;
                            case 11: nextStr = v[0]; break;
                            case 19: sourceName = v[0]; break;
                        }
                        break;
                    }
                }
            }
            if (!java.util.Arrays.equals(previousList, list)) {
                listCountAtSelection = list == null ? 0 : count;
            }
        }
    }

    /** one maneuver (DiPO e/v) */
    static final class Maneuver {
        private static final java.util.regex.Pattern ROAD_ENTRY = java.util.regex.Pattern.compile(
                "(지하|고가)\\s*차도\\s*(?:(?:으로|로)\\s*)?진입(?:하세요|하십시오)?(?![가-힣])");
        int index, type, distance, distUnits, side, junction, exit, linkedLane;
        String description, afterRoad, distStr;
        int[] angles;
        boolean hasExit;

        /** Log facility hints without exporting route text. */
        String facilityTrace() {
            return " tollText=" + tollText() + " restText=" + restText()
                    + " underpassText=" + hasRoadText("지하차도")
                    + " overpassText=" + hasRoadText("고가차도")
                    + " roadsideText=" + (hasRoadText("옆길") || hasRoadText("옆 길") || hasRoadText("측도"))
                    + " description=" + description + " afterRoad=" + afterRoad
                    + " angles=" + java.util.Arrays.toString(angles);
        }

        String displayRoad() {
            String road = afterRoad == null ? "" : afterRoad;
            if (!road.trim().isEmpty()) return road;
            if (hasRoadText("지하차도") && !hasRoadText("고가차도")) return "지하차도";
            if (hasRoadText("고가차도") && !hasRoadText("지하차도")) return "고가차도";
            if (restText() && !tollText()) return hasRoadText("졸음쉼터") ? "졸음쉼터" : "휴게소";
            if (junction == 0 && (type == 8 || type == 22 || type == 23)) return "고속도로 진출";
            return road;
        }

        private boolean hasRoadText(String token) {
            return (description != null && description.contains(token))
                    || (afterRoad != null && afterRoad.contains(token));
        }

        int roadFacilityIcon() {
            if ((hasRoadText("지하차도") || hasRoadText("고가차도"))
                    && (hasRoadText("옆길") || hasRoadText("옆 길") || hasRoadText("측도"))
                    && !hasRoadText("금지") && !restText()) {
                // Trial right-side road glyph observed in the stock comparison drive.
                return type == 14 ? 84 : 0;
            }
            if (description == null || restText() || description.contains("옆길")
                    || description.contains("측도") || description.contains("금지")) return 0;
            java.util.regex.Matcher entry = ROAD_ENTRY.matcher(description);
            if (!entry.find()) return 0;
            String kind = entry.group(1);
            if (entry.find()) return 0;
            // The stock navigation sent 44 on the final approach to a reported overpass.
            return kind.equals("고가") ? 44 : 0;
        }

        private boolean tollText() {
            return hasTollText(description) || hasTollText(afterRoad);
        }

        private boolean restText() {
            return hasRestText(description) || hasRestText(afterRoad);
        }

        private static boolean hasTollText(String text) {
            return text != null && (text.contains("요금소") || text.contains("톨게이트")
                    || text.contains("자동징수") || text.contains("풍세TG"));
        }

        private static boolean hasRestText(String text) {
            return text != null && (text.contains("휴게소") || text.contains("졸음쉼터"));
        }

        Maneuver(int index, int id, Iap2NotiMsg msg) {
            if (msg == null) return;
            clear();
            read(index, id, msg);
        }

        void clear() {
            index = -1;
            description = "";
            type = 0;
            afterRoad = "";
            distance = 0;
            distStr = "";
            distUnits = 2;
            side = 0;
            junction = 0;
            angles = null;
            exit = 0;
            linkedLane = 0;
        }

        /** as DiPO, the exit angle resets with every update (it's only kept if this one carries it) */
        void read(int index, int id, Iap2NotiMsg msg) {
            Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
            this.index = index;
            exit = 0;
            hasExit = false;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                int item = it.getItemId(), n = it.getItemObjCount();
                switch (it.getItemParamType()) {
                    case 1: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (item == 10) {
                            if (v != null) {
                                angles = new int[n];
                                for (int j = 0; j < n; j++) angles[j] = v[j];
                            } else {
                                angles = null;
                            }
                        } else if (item == 11 && v != null) {
                            exit = v[0];
                            hasExit = true;
                        }
                        break;
                    }
                    case 5: {
                        int[] v = it.getItemIntArray();
                        if (item == 12 && v != null) linkedLane = v[0];
                        break;
                    }
                    case 6: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (item == 5 && v != null) distance = v[0];
                        break;
                    }
                    case 8: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (v == null) break;
                        switch (item) {
                            case 3: type = v[0]; break;
                            case 7: distUnits = v[0]; break;
                            case 8: side = v[0]; break;
                            case 9: junction = v[0]; break;
                        }
                        break;
                    }
                    case 10: {
                        String[] v = it.getItemStringArray();
                        if (v == null) break;
                        switch (item) {
                            case 2: description = v[0]; break;
                            case 4: afterRoad = v[0]; break;
                            case 6: distStr = v[0]; break;
                        }
                        break;
                    }
                }
            }
        }
    }
}
