# HMG Phone Projection TBT

[English](README.en.md) · [한국어](README.md)

An HMG-API community mod that forwards phone navigation guidance to the instrument cluster. It includes separate CarPlay and Android Auto receivers. Output depends on what each navigation app sends and on the vehicle's HMG-API map and cluster.

This mod does **not** enable wireless projection. Wireless CarPlay or Android Auto must already connect to the head unit.

## App and connection support

Status below distinguishes code paths from vehicle confirmation. “Implemented” means the receiver handles the available app data; it does not promise identical cluster output for every route.

| Projection / app | Current turn | Next turn | Trip distance | Lane data | Latest status and known issues |
| --- | --- | --- | --- | --- | --- |
| CarPlay · NAVER Map | Implemented; vehicle-tested | Implemented when the app sends an adjacent maneuver; startup handling improved in 0.1.57 | Implemented when supplied | Implemented; composite-arrow colors supported. Pocket markers need matching fresh factory data | Most complete CarPlay path. Latest startup, lane and facility changes still need a 0.1.58 vehicle retest. Service-area symbol is unresolved; some grade-separated-road details are absent from the phone data. |
| CarPlay · TMAP | Implemented | Implemented when received; intermittent absence has been reported | Implemented when supplied | Partial: route-usable/recommended directions have been missing; 0.1.55 fixes a recorded straight/left and straight/right mapping case | Tollgate and service-area symbols remain unreliable. Roundabout and lane behavior need app-specific vehicle retesting. |
| CarPlay · Kakao Navi | Implemented | Implemented when received; startup fallback added in 0.1.57 | Implemented when supplied | App-specific lane output is not verified | A recorded roundabout event stopped TBT output when the angle was absent. 0.1.58 keeps non-directional text/distance instead; this fix has not yet been confirmed in the vehicle. |
| Android Auto · NAVER Map | Experimental receiver implemented | Receiver handles ahead-turn callbacks, but the tested setup often showed no next turn | Receiver handles trip-distance callbacks; vehicle output is inconsistent/unverified | Lane callback supported; recommended-lane behavior is unverified | User testing reported missing next turn and unreliable remaining distance. Route-stop loading and Korean text were also reported; text decoding was adjusted, but latest vehicle behavior is unverified. |
| Android Auto · TMAP | Experimental receiver implemented | Receiver handles ahead-turn callbacks; user testing reported no next turn | Receiver handles trip-distance callbacks; vehicle output is unverified | Lane callback supported; recommended-lane behavior is unverified | User testing reported TBT loading and missing next turn. Do not treat as reliable yet. |
| Android Auto · Kakao Navi | Generic receiver path only | Not tested | Not tested | Not tested | App-specific support and vehicle behavior are unknown. |

### General display behavior

- CarPlay and Android Auto use separate navigation-data receivers and a shared cluster output layer.
- Lane colors: gray means the direction leaves the route, white means route-usable, and blue means app-recommended. The app must provide enough lane data; the mod does not infer missing recommendations.
- Factory pocket-lane markers are copied only when recent factory data has a compatible lane count and direction layout.
- Service-area symbol, tollgate icon, roundabout drawings, and overpass/underpass side-road distinctions are not reliable across apps. Missing app metadata is not guessed.

## Tested vehicle and compatibility

| Region | Vehicle | AVN software |
| --- | --- | --- |
| Korea (KOR) | 2023 Hyundai Palisade LX2 PE, factory Gen5W | `LX2PE.KOR.0000.V039.001.260902` |

Other vehicles, clusters, regions and firmware are unverified. The installer does not restrict model, region or firmware, but this is not a compatibility claim. Requires [HMG-API](https://github.com/VysseCodes/HMG-API) 1.0.2 or later and **Hooks on (map checked)** for the vehicle. Native fixes are guarded to the verified library build.

## Installation

- **First install:** install HMG-API first, then install `phone-projection-tbt-0.1.58-nemesis-package.zip` using NEMESIS.
- **Update an existing TBT installation:** copy `phone-projection-tbt-0.1.58.zip` to USB `HMGAPI/updates/` without extracting it. Remove older Phone Projection TBT update ZIPs. While parked, select **Check USB updates → Apply changes → Restart Unit**.
- **Source:** `phone-projection-tbt-0.1.58-source.zip` contains the source and host-side regression tests.

For NAVER Map on CarPlay, enable its CarPlay HUD integration and select the cluster navigation page. Keep other projection-mod TBT output disabled to avoid duplicate cluster updates. The existing mod ID and signing identity are retained for in-place updates.

## Known limitations and unverified behavior

- Version 0.1.58 has passed host-side regression checks, compilation and signature verification. The latest Kakao roundabout fallback, upcoming-turn fallback and startup changes still need vehicle confirmation.
- Android Auto is experimental. Naver Map and TMAP have known user-reported missing next turns; TMAP has shown a loading state. Remaining distance, lane recommendations and stopping guidance need vehicle validation. Kakao on Android Auto has not been tested.
- The standalone service-area icon has not been confirmed. Code 183 appeared as an exit diagram; 181 and 182 did not produce a confirmed standalone symbol in the tested runs.
- Tollgates, rest areas, tunnels and grade-separated side roads may arrive as generic maneuvers. A specific pictogram cannot be selected reliably without distinguishable source data.
- An unprotected-left attribute is shown only if the navigation app sends it.
- Some lane recommendations or pocket-lane details may not be sent by the app or may lack matching factory data.

## Report another vehicle or a problem

Open a [GitHub issue](https://github.com/ch0iiih/HMG_Phone_Projection_TBT/issues/new) titled `[region / vehicle code / firmware] symptom`. Include vehicle year, region, cluster, complete AVN firmware, HMG-API/mod/app versions, connection type, expected and actual output, reproduction steps and event time. Attach logs or timed photos when useful. Logs can include route text and location; redact personal data before posting.

## Development

See [BUILD.md](BUILD.md), [CHANGELOG.md](CHANGELOG.md), and [NOTICE.md](NOTICE.md). Licensed under [AGPL-3.0](LICENSE).
