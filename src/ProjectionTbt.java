package hmgapi.mods.projection;

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
import hmgapi.mods.projection.carplay.StockLaneHints;

public final class ProjectionTbt extends ModEntry {
    private byte[] stockLaneFrame;
    private long stockLaneFrameAt, stockLaneDistanceAt, stockLanePublishedAt;
    private int stockLaneDistance = -1;
    private boolean stockLanePublishFailed;

    private synchronized void captureLaneStructure(ModContext mod, LoadedApp app, String name, Object[] args) {
        if (!"sendClusterData".equals(name) && !"sendNaviInsideData".equals(name)) return;
        if (args.length == 0 || !(args[0] instanceof Integer)) return;
        int type = (Integer) args[0];
        long now = android.os.SystemClock.elapsedRealtime();
        if (type == 6127) {
            for (Object arg : args) if (arg instanceof byte[] && ((byte[])arg).length == 81) {
                stockLaneFrame = ((byte[])arg).clone();
                stockLaneFrameAt = now;
                break;
            }
        } else if (type == 6118) {
            for (Object arg : args) if (arg instanceof int[] && ((int[])arg).length >= 1) {
                stockLaneDistance = ((int[])arg)[0];
                stockLaneDistanceAt = now;
                break;
            }
        } else return;
        if (now - stockLanePublishedAt < 500 || stockLaneFrame == null) return;
        try {
            long lease = Settings.Global.getLong(app.context.getContentResolver(), LANE_LEASE, 0);
            if (lease <= System.currentTimeMillis()) return;
            boolean published = Settings.Global.putString(app.context.getContentResolver(), StockLaneHints.KEY,
                    StockLaneHints.encode(stockLaneFrameAt, stockLaneDistanceAt,
                            stockLaneDistance, stockLaneFrame));
            if (!published && !stockLanePublishFailed)
                mod.log("Factory pocket hint publish failed");
            stockLanePublishFailed = !published;
            stockLanePublishedAt = now;
        } catch (RuntimeException error) {
            // Optional factory hints must never interrupt stock JNI callbacks.
            if (!stockLanePublishFailed) mod.error("Factory pocket hint publish", error);
            stockLanePublishFailed = true;
        }
    }
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
                    private boolean roadTraceFailed;

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
                        if (name.equals("updateLaneInfo") && arity == 1 && p.args[0] instanceof byte[]) {
                            detail = " frame=" + java.util.Arrays.toString((byte[]) p.args[0]);
                        } else if (name.equals("updateTBTSubInfo2C") && arity == 9) {
                            detail = " slots=" + java.util.Arrays.toString(p.args);
                        } else if (trip) {
                            detail = " args=" + arity + " distance=" + p.args[0]
                                    + " distanceErr=" + p.args[1] + " unit=" + p.args[2];
                        } else if (name.equals("updateTPCrossroadName") && arity == 1 && p.args[0] != null) {
                            try {
                                String road = (String) Points.field(null, "framework.crossroad_name.name").get(p.args[0]);
                                if (road != null) {
                                    detail = " underpassText=" + road.contains("지하차도")
                                            + " overpassText=" + road.contains("고가차도")
                                            + " restText=" + (road.contains("휴게소") || road.contains("졸음쉼터"))
                                            + " tollText=" + (road.contains("요금소") || road.contains("톨게이트"));
                                }
                            } catch (Exception e) {
                                if (!roadTraceFailed) mod.error("Native crossroad trace", e);
                                roadTraceFailed = true;
                            }
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

    private void observeStockGuidance(final ModContext mod, final LoadedApp app) {
        try {
            Class<?> nativeCall = Hooks.findClass("com.mnsoft.navi.NativeCall", app.classLoader);
            for (final Method method : nativeCall.getDeclaredMethods()) {
                final String name = method.getName();
                if (!name.equals("sendClusterData") && !name.equals("sendClusterParser")
                        && !name.equals("sendNaviInsideData") && !name.equals("sendNaviInsideDataByte")
                        && !name.equals("sendNaviInsideDataSub")) continue;
                final boolean parser = name.equals("sendClusterParser");
                Class<?>[] types = method.getParameterTypes();
                if (parser) {
                    if (!java.util.Arrays.equals(types, new Class<?>[]{byte[].class, int.class})) continue;
                } else {
                    if (types.length < 1 || types[0] != int.class) continue;
                    boolean arrays = true;
                    for (int i = 1; i < types.length; i++) if (!types[i].isArray()) arrays = false;
                    if (!arrays) continue;
                }
                Bridge.hookMethod(method, new MethodHook() {
                    private final java.util.Map<Integer, String> previous = new java.util.HashMap<>();
                    private final java.util.Map<Integer, Long> sampled = new java.util.HashMap<>();

                    @Override
                    protected synchronized void beforeHookedMethod(Param p) {
                        captureLaneStructure(mod, app, name, p.args);
                        int type = (Integer) p.args[parser ? 1 : 0];
                        long now = android.os.SystemClock.elapsedRealtime();
                        Long last = sampled.get(type);
                        if (last != null && now - last < 500) return;
                        String value = guidanceSnapshot(p.args);
                        if (value.equals(previous.get(type))) return;
                        if (previous.size() >= 128 && !previous.containsKey(type)) {
                            previous.clear();
                            sampled.clear();
                        }
                        previous.put(type, value);
                        sampled.put(type, now);
                        mod.log("Stock guidance " + name + " type=" + type + " args=" + value);
                    }
                });
                mod.log("Stock guidance capture ready " + name);
            }
        } catch (RuntimeException e) {
            mod.error("Stock guidance capture unavailable", e);
        }
    }

    private static String guidanceSnapshot(Object[] args) {
        StringBuilder out = new StringBuilder();
        for (Object arg : args) {
            if (out.length() > 0) out.append(' ');
            if (arg == null || !arg.getClass().isArray()) {
                out.append(String.valueOf(arg));
                continue;
            }
            int length = java.lang.reflect.Array.getLength(arg);
            out.append("array(").append(length).append(")[");
            for (int i = 0; i < length && i < 128; i++) {
                if (i > 0) out.append(',');
                String value = String.valueOf(java.lang.reflect.Array.get(arg, i));
                out.append(value.length() > 240 ? value.substring(0, 240) + "..." : value);
                if (out.length() > 2200) break;
            }
            out.append(']');
            if (out.length() > 2200) {
                out.append(" [truncated]");
                break;
            }
        }
        return out.toString();
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
        if ("com.mnsoft.navi".equals(app.packageName)) {
            observeNativeDisplay(mod, app);
            observeStockGuidance(mod, app);
        }
        if ("com.tcc.iap2server".equals(app.packageName)) {
            observeNativeRouteEvents(mod, app);
            final LaneDispatchPatch lanePatch = LaneDispatchPatch.matchesStockLibrary()
                    ? new LaneDispatchPatch() : null;
            if (lanePatch != null) {
                mod.nativePatch(app, "libiap2session.so");
                lanePatch.apply(mod);
            } else {
                mod.log("TBT native lane fixes skipped: unexpected libiap2session.so hash");
            }
            mod.hook(app.classLoader, "iap2.init_info.check_supports_features", new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    try {
                        if (lanePatch != null) lanePatch.apply(mod);
                        Points.field(app.classLoader, "iap2.init_info.route_guidance").setInt(null, 1);
                        mod.log("CarPlay: iAP2 route guidance offered");
                    } catch (Exception e) {
                        mod.error("CarPlay route guidance flag", e);
                    }
                }
            });
        } else if ("com.daudio.app.dipo".equals(app.packageName)) {
            try {
                mod.appPart(app, "hmgapi.mods.projection.carplay.Dipo")
                    .getMethod("start", ModContext.class, LoadedApp.class).invoke(null, mod, app);
            } catch (Exception e) {
                mod.error("CarPlay DiPO route guidance", e);
            }
        }
    }
}
