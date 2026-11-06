package hmgapi.mods.wired;

import hmgapi.hook.Bridge;
import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;
import hmgapi.hook.Points;

import android.provider.Settings;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class WiredTbt extends ModEntry {
    public static final String LANE_LEASE = "hmgapi_lx2pe_carplay_lane_until";
    public static final long LANE_LEASE_MS = 12000;

    private void observeStatus(final ModContext mod, final LoadedApp app,
            final String method, Class<?> parameter) {
        try {
            Hooks.findAndHookMethod("com.hkmc.cluster.NavigationCluster", app.classLoader,
                    method, parameter, new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    mod.log("Cluster trace " + app.packageName + " " + method
                            + "=" + p.args[0] + " completed=" + (p.getThrowable() == null));
                }
            });
            mod.log("Cluster trace ready " + app.packageName + " " + method);
        } catch (Exception e) {
            mod.error("Cluster trace unavailable " + app.packageName + " " + method, e);
        }
    }

    private void observeNativeDisplay(final ModContext mod, final LoadedApp app) {
        Class<?> cluster;
        try {
            cluster = Hooks.findClass("com.hkmc.cluster.NavigationCluster", app.classLoader);
        } catch (Exception e) {
            mod.error("Native display trace unavailable", e);
            return;
        }
        for (Method method : cluster.getDeclaredMethods()) {
            final String name = method.getName();
            if (!name.matches("update(TBT.*|TP.*|LaneInfo|RemainDis)|.*(?:Navi|Route|Guidance|Focus).*")
                    || name.equals("responseNaviAliveStatus") || Modifier.isAbstract(method.getModifiers())
                    || Modifier.isNative(method.getModifiers())) continue;
            final int arity = method.getParameterTypes().length;
            final boolean trip = name.equals("updateTBTSubInfo") && (arity == 9 || arity == 10);
            try {
                Bridge.hookMethod(method, new MethodHook() {
                    private boolean suppressionLogged;
                    private boolean leaseReadFailed;

                    @Override
                    protected void beforeHookedMethod(Param p) {
                        boolean emptyTurn = name.equals("updateTBTInfo") && arity == 9
                                && Integer.valueOf(8).equals(p.args[0]);
                        boolean emptyTrip = trip && arity == 10 && Float.valueOf(0f).equals(p.args[0])
                                && Integer.valueOf(-3).equals(p.args[1])
                                && Integer.valueOf(15).equals(p.args[2]);
                        boolean lane = name.equals("updateLaneInfo") && arity == 1;
                        if (!emptyTurn && !emptyTrip && !lane) return;
                        String lease = lane ? LANE_LEASE : emptyTurn ? "hmgapi_lx2pe_carplay_turn_until"
                                : "hmgapi_lx2pe_carplay_distance_until";
                        long limit = lane ? LANE_LEASE_MS : emptyTurn ? 2500 : 5000;
                        try {
                            long remaining = Settings.Global.getLong(app.context.getContentResolver(),
                                    lease, 0) - System.currentTimeMillis();
                            if (remaining <= 0 || remaining > limit) {
                                suppressionLogged = false;
                                return;
                            }
                        } catch (RuntimeException e) {
                            if (!leaseReadFailed) mod.error("Native display lease read", e);
                            leaseReadFailed = true;
                            return;
                        }
                        if (!suppressionLogged) mod.log("Native display write suppressed during CarPlay method=" + name);
                        suppressionLogged = true;
                        p.setResult(null);
                    }

                    private String previous;
                    private long lastLogged;

                    @Override
                    protected synchronized void afterHookedMethod(Param p) {
                        String detail = "";
                        if (name.equals("updateTBTSubInfo2C") && arity == 9) {
                            detail = " slots=" + java.util.Arrays.toString(p.args);
                        } else if (trip) {
                            detail = " args=" + arity + " distance=" + p.args[0]
                                    + " distanceErr=" + p.args[1] + " unit=" + p.args[2];
                        } else if ((name.equals("updateTBTInfo") || !name.startsWith("update"))
                                && p.args.length > 0
                                && (p.args[0] instanceof Number || p.args[0] instanceof Boolean)) {
                            detail = " first=" + p.args[0];
                        }
                        String result = detail + " completed=" + (p.getThrowable() == null);
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (result.equals(previous) && now - lastLogged < (trip ? 2500 : 10000)) return;
                        previous = result;
                        lastLogged = now;
                        mod.log("Native display " + name + result);
                    }
                });
                mod.log("Native display trace ready " + name + "/" + method.getParameterTypes().length);
            } catch (RuntimeException e) {
                mod.error("Native display trace unavailable " + name, e);
            }
        }
    }

    private void observeNativeRouteEvents(final ModContext mod, LoadedApp app) {
        try {
            Hooks.findAndHookMethod("com.tcc.iap2server.Iap2Service", app.classLoader,
                    "postEventFromNative", int.class, int.class, int.class, Object.class,
                    new MethodHook() {
                private int seen;

                @Override
                protected synchronized void beforeHookedMethod(Param p) {
                    int event = (Integer) p.args[0];
                    if (event < 120 || event > 124) return;
                    int bit = 1 << (event - 120);
                    if ((seen & bit) != 0) return;
                    seen |= bit;
                    mod.log("TBT lane trace native iAP2 event=" + event
                            + " hasPayload=" + (p.args[3] != null));
                }
            });
            mod.log("TBT lane trace native iAP2 hook ready");
        } catch (RuntimeException e) {
            mod.error("TBT lane trace native iAP2 hook unavailable", e);
        }
    }

    @Override
    public void appLoaded(final ModContext mod, final LoadedApp app) {
        if ("com.mnsoft.navi".equals(app.packageName) || "com.daudio.app.dipo".equals(app.packageName)) {
            observeStatus(mod, app, "updateOperationStatus", int.class);
            observeStatus(mod, app, "responseNaviAliveStatus", boolean.class);
        }
        if ("com.mnsoft.navi".equals(app.packageName)) observeNativeDisplay(mod, app);
        if ("com.tcc.iap2server".equals(app.packageName)) {
            observeNativeRouteEvents(mod, app);
            mod.nativePatch(app, "libiap2session.so");
            final LaneDispatchPatch lanePatch = new LaneDispatchPatch();
            lanePatch.apply(mod);
            mod.hook(app.classLoader, "iap2.init_info.check_supports_features", new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    try {
                        lanePatch.apply(mod);
                        Points.field(app.classLoader, "iap2.init_info.route_guidance").setInt(null, 1);
                        mod.log("Wired CarPlay: iAP2 route guidance offered");
                    } catch (Exception e) {
                        mod.error("Wired CarPlay route guidance flag", e);
                    }
                }
            });
        } else if ("com.daudio.app.dipo".equals(app.packageName)) {
            try {
                mod.appPart(app, "hmgapi.mods.wireless.dipo.Dipo")
                    .getMethod("start", ModContext.class, LoadedApp.class).invoke(null, mod, app);
            } catch (Exception e) {
                mod.error("Wired CarPlay DiPO route guidance", e);
            }
        }
    }
}
