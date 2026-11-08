package hmgapi.mods.wireless.dipo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.tcc.iap2.IIap2Listener;
import com.tcc.iap2.IIap2Service;
import com.tcc.iap2.Iap2ListenerInfo;
import com.tcc.iap2.Iap2NotiMsg;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;

public final class Dipo {
    private Dipo() {}

    static final String IAP2_ACTION = "com.remote.service.IAP2SERVICE";
    /** iAP2 listener type for route guidance (notifications 120, 121, 122) */
    static final int LISTENER_ROUTE_GUIDANCE = 4096;
    static final int RG_UPDATE = 120, RG_MANEUVER = 121, RG_LANES = 122;
    static final String[] RG_COMPONENTS = { "RouteGuidanceComponent0" };

    static ModContext mod;
    static LoadedApp app;
    static volatile IIap2Service iap2;
    static boolean tbt = true;
    static Handler turnsHandler;
    static CpTurns turns;
    static RgListener rg;
    /** set while HMG-API itself calls the iAP2 server (its own route guidance restart) */
    static final ThreadLocal<Boolean> OWN = new ThreadLocal<Boolean>();

    static void log(String m) {
        mod.log(m);
    }

    static void error(String m, Throwable t) {
        mod.error(m, t);
    }

    /** Wireless.appLoaded, in DiPO's process */
    public static void start(ModContext m, LoadedApp a) {
        mod = m;
        app = a;
        turnByTurn();
        if (!tbt) return;
        mod.hook(app.classLoader, "dipo.carplay.session_started", new MethodHook() {
            @Override
            protected void afterHookedMethod(Param p) {
                turnsHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        IIap2Service service = iap2;
                        if (service == null) {
                            log("Wired CarPlay: session started before iAP2 binding");
                            return;
                        }
                        try {
                            log("Wired CarPlay: session route update request=" +
                                    service.Iap2SvcStartRouteGuidanceUpdate(RG_COMPONENTS, true, true));
                        } catch (Exception e) {
                            error("Wired CarPlay: session route update request", e);
                        }
                    }
                }, 1000);
            }
        });
        Intent i = new Intent(IAP2_ACTION);
        boolean bound = a.context.bindService(i, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder b) {
                IIap2Service s = IIap2Service.Stub.asInterface(b);
                iap2 = s;
                if (tbt) registerRouteGuidance(s);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                iap2 = null;
                post(new Runnable() {
                    @Override
                    public void run() {
                        turns.disconnected();
                    }
                });
                log("DiPO: iAP2 server gone");
            }
        }, Context.BIND_AUTO_CREATE);
        log("DiPO: iAP2 server " + (bound ? "binding" : "can't be bound"));
    }

    private static void turnByTurn() {
        if (Car.stepOfSpec() >= 999) {
            log("CarPlay: stepofspec is 999 or more, DiPO's own turn-by-turn runs (HMG-API's stays off)");
            tbt = false;
            return;
        }
        Cluster c = Cluster.of(app.context);
        if (c == null) {
            log("Wired CarPlay: cluster service unavailable");
            tbt = false;
            return;
        }
        HandlerThread t = new HandlerThread("hmgapi-cp-tbt");
        t.start();
        turnsHandler = new Handler(t.getLooper());
        turns = new CpTurns(app.context, c, new Runnable() {
            @Override
            public void run() {
                restartRouteGuidance();
            }
        });
        rg = new RgListener();
        mod.hook(app.classLoader, "dipo.iap2_proxy.stop_route_guidance", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (OWN.get() != null) return;
                post(new Runnable() {
                    @Override
                    public void run() {
                        turns.stop("DiPO stop hook");
                    }
                });
            }
        });
        log("Wired CarPlay TBT 0.1.41: route guidance listener enabled");
    }

    static void post(Runnable r) {
        final Runnable run = r;
        turnsHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    run.run();
                } catch (Throwable x) {
                    error("CarPlay turn-by-turn", x);
                }
            }
        });
    }

    static void registerRouteGuidance(IIap2Service s) {
        try {
            Iap2ListenerInfo info = new Iap2ListenerInfo();
            info.onSetSupportType(LISTENER_ROUTE_GUIDANCE);
            info.onSetListenerName("HMGAPI_RG");
            log("CarPlay: Iap2SvcInit(HMGAPI_RG, 0x1000) = " + s.Iap2SvcInit(rg, info));
        } catch (Throwable t) {
            error("CarPlay: route guidance listener", t);
        }
    }

    /** after a source change: Stop, then Start, so the phone sends its whole current state (the build's HmgApiRgRestart) */
    static void restartRouteGuidance() {
        IIap2Service s = iap2;
        if (s == null) return;
        OWN.set(Boolean.TRUE);
        try {
            s.Iap2SvcStopRouteGuidanceUpdate(RG_COMPONENTS);
            Thread.sleep(200);
            s.Iap2SvcStartRouteGuidanceUpdate(RG_COMPONENTS, true, true);
        } catch (Throwable t) {
            // the session went away meanwhile
        } finally {
            OWN.remove();
        }
    }

    /** HMG-API's iAP2 listener for route guidance: everything goes to CpTurns on its thread */
    static final class RgListener extends IIap2Listener.Stub {
        private int seenTypes;
        private String lastRouteFields;
        private int packetSamples;
        @Override
        public void connected(int component, String serial) {}

        @Override
        public void disconnected(final int component) {
            // as DiPO's listener: component 2 isn't a session, and the bridge's own sessions end while wireless CarPlay runs
            if (component == 2) return;
            post(new Runnable() {
                @Override
                public void run() {
                    turns.disconnected();
                }
            });
        }

        @Override
        public void notify(final int what, int a1, int a2, final Iap2NotiMsg msg) {
            if ((what != RG_UPDATE && what != RG_MANEUVER && what != RG_LANES) || msg == null) return;
            post(new Runnable() {
                @Override
                public void run() {
                    int typeBit = 1 << (what - RG_UPDATE);
                    if ((seenTypes & typeBit) == 0) {
                        seenTypes |= typeBit;
                        log("Wired CarPlay: first route guidance event type=" + what);
                    }
                    if (packetSamples < 16 && (what == RG_UPDATE || what == RG_MANEUVER)) {
                        packetSamples++;
                        Iap2NotiMsg.Iap2ObjGroup group = msg.getItemGroup();
                        StringBuilder fields = new StringBuilder();
                        for (int i = 0; group != null && i < group.getItemCnt() && i < 24; i++) {
                            Iap2NotiMsg.Iap2ObjItem item = group.getObjItem(i);
                            if (item == null) continue;
                            int id = item.getItemId(), type = item.getItemParamType();
                            fields.append(' ').append(id).append('/').append(type)
                                  .append('/').append(item.getItemObjCount());
                            if (what == RG_UPDATE && id == 1 && type == 8) {
                                int[] value = item.getItemIntArray();
                                if (value != null && value.length > 0) fields.append('=').append(value[0]);
                            }
                        }
                        log("Wired CarPlay: packet " + what + " fields id/type/count:" + fields);
                    }
                    if (what == RG_UPDATE || what == RG_MANEUVER) {
                        Iap2NotiMsg.Iap2ObjGroup group = msg.getItemGroup();
                        StringBuilder raw = new StringBuilder();
                        for (int k = 0; group != null && k < group.getItemCnt(); k++) {
                            Iap2NotiMsg.Iap2ObjItem item = group.getObjItem(k);
                            if (item == null) continue;
                            int id = item.getItemId();
                            boolean selected = what == RG_MANEUVER
                                    ? (id == 0 || id == 1 || id == 3 || id == 8 || id == 9 || id == 11 || id == 12)
                                    : (id == 0 || id == 1 || id == 13 || id == 14 || id == 16 || id == 17 || id == 18);
                            if (!selected) continue;
                            int type = item.getItemParamType();
                            if (type != 1 && type != 5 && type != 8 && type != 9) continue;
                            int[] values = item.getItemIntArray();
                            raw.append(" id=").append(id).append("/type=").append(type)
                                    .append("/values=").append(java.util.Arrays.toString(values));
                        }
                        String record = raw.toString();
                        if (what == RG_MANEUVER) {
                            log("TBT 0.1.41 received maneuver" + record);
                        } else if (!record.equals(lastRouteFields)) {
                            log("TBT 0.1.41 received route" + record);
                            lastRouteFields = record;
                        }
                    }
                    if (what == RG_UPDATE) turns.routeGuidance(msg);
                    else if (what == RG_MANEUVER) turns.maneuver(msg);
                    else turns.lanes(msg);
                }
            });
        }
    }

}
