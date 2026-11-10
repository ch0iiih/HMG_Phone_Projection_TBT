# HMG Phone Projection TBT

[English](README.en.md) · [한국어](README.md)

An HMG-API community mod that sends korean phone navigation guidance to the instrument cluster: current and next turns, remaining trip distance, and lane guidance supplied by the navigation app. HUD output depends on the vehicle; the instrument cluster is the physically tested display on the vehicle listed below.

## Projection support

| Connection | Status |
| --- | --- |
| Wired CarPlay | Implemented and tested with NAVER Map |
| Wireless CarPlay | Uses the same CarPlay guidance listener; the development vehicle has moved to wireless CarPlay. Verification of the latest display changes is ongoing. |
| Android Auto TBT | **Planned, not implemented in this release** |

This mod does not enable wireless phone projection itself. Wireless CarPlay must already work on the head unit, through a separate compatible setup or an adapter.

## Tested vehicle and region

| Region | Vehicle | AVN software |
| --- | --- | --- |
| Korea (KOR) | 2023 Hyundai Palisade LX2 PE, factory Gen5W | `LX2PE.KOR.0000.V039.001.260902` |

Other regions, vehicles, clusters and firmware versions are unverified. General turns and roundabouts have been observed in earlier builds. Recent lane changes and the alternative service-area icon still need vehicle verification. Grade-separated-road and unprotected-turn attributes cannot be reconstructed when the phone only sends a generic maneuver.

## Installation

Requires [HMG-API](https://github.com/VysseCodes/HMG-API) **1.0.2 or later** and **Hooks on (map checked)** for your vehicle.

Starting with **0.1.46**, both installers accept any model, region, and firmware. Runtime behavior depends on the HMG-API map, DiPO interfaces, and cluster. The native lane fixes require the verified `libiap2session.so` build.

- **First installation:** with HMG-API already installed, install `phone-projection-tbt-0.1.46-nemesis-package.zip` through NEMESIS.
- **Update an existing TBT installation:** copy `phone-projection-tbt-0.1.46.zip` to the USB drive's `HMGAPI/updates/` folder without extracting it. Remove previous TBT update ZIPs. In HMG-API select **Check USB updates → Apply changes → Restart Unit** while parked.

Connect the iPhone using wired or already-working wireless CarPlay and start navigation. For NAVER Map, enable its CarPlay HUD integration and select the navigation page on the cluster.

The displayed name is now **Phone Projection TBT**. The legacy internal mod ID and author signature are unchanged, so the existing installation updates in place. To roll back, disable or remove the mod in HMG-API and apply changes, then restart the head unit.

## Lane display

- **Gray:** direction does not follow the route.
- **White:** usable direction that follows the route.
- **Blue:** preferred direction recommended by the navigation app.

Composite arrows keep their separate direction colors. Factory pocket-lane markers are copied only when fresh factory data has matching lane count and compatible directions; phone arrows and recommendations stay unchanged.

## Known limitations

- Service-area display code 183 was physically observed as an exit diagram. The alternative native code 181 is under test; a standalone service-area symbol is **not yet confirmed**.
- Overpass, underpass and side-road symbols require distinguishable received data. Some NAVER Map screen graphics are not represented in the logged CarPlay maneuver fields.
- Unprotected left turns remain ordinary left turns when no corresponding attribute is received.
- Android Auto TBT is a future implementation, not a feature enabled by this project rename.

## Report another vehicle or a problem

Open a [GitHub issue](https://github.com/ch0iiih/HMG_Phone_Projection_TBT/issues/new) titled `[region / vehicle code / firmware] symptom`, and include:

- Vehicle, model year, region, cluster type and complete AVN firmware version.
- HMG-API/mod/iOS/navigation-app versions, and wired/native-wireless/adapter connection type.
- Expected and actual behavior, reproduction steps, and the time of the event.
- Logs and, if useful, a photo with the capture time. Create `TBT-LOGS` at the USB root and reconnect the drive to export `phone-projection-tbt.log` and `stock-guidance.log`. Logs may include route text and location; redact personal data before posting.

## Development

See [BUILD.md](BUILD.md) and [CHANGELOG.md](CHANGELOG.md). Android Auto will need its own guidance receiver/conversion path before sharing cluster output functionality.

Licensed under [AGPL-3.0](LICENSE). CarPlay guidance code derives from the Wireless CarPlay + Android Auto mod in [HMG-API](https://github.com/VysseCodes/HMG-API).
