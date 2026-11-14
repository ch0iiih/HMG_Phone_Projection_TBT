# Phone Projection TBT v0.1.58 · Prerelease

## English

This release updates the CarPlay maneuver fallback and includes the experimental Android Auto receiver introduced in earlier builds. It is still a prerelease: several app and cluster behaviors below need another vehicle test.

### Changes

- Keep Kakao Navi roundabout entry/exit text and distance live when CarPlay maneuver types 6/7 arrive without a junction flag or exit angle. Use a non-directional cluster type when the angle is missing; do not invent a straight exit.
- Use an explicit exit angle for a directional roundabout icon when available, even if the junction flag is absent. Unsupported traffic-side geometry stays non-directional.
- Use the current-maneuver mapping as the upcoming-turn fallback. This covers CarPlay slight-left/right, sharp turns and end-of-road turns that previously vanished from the small next-turn slot.
- Preserve route-start text and distance when type 11 has no directional icon.
- Earlier updates in this release line add CarPlay startup handling, CarPlay lane mapping fixes, Android Auto callback reception and lifecycle cleanup, Korean road-text decoding, and per-arrow gray/white/blue lane colors. See `CHANGELOG.md` for version-by-version details.

### App support and status

| Projection / app | Implemented | Vehicle status / open issues |
| --- | --- | --- |
| CarPlay · NAVER Map | Current/next maneuvers, remaining trip distance and received lane data | Previously tested on the development vehicle. 0.1.58 startup/lane changes need retest; pocket lanes require matching factory data; service-area symbol unresolved. |
| CarPlay · TMAP | Current/next maneuvers, remaining distance and lane-frame conversion | Recorded straight/left and straight/right mapping fix in 0.1.55. User reported missing route-usable/recommended arrows, tollgate/rest-area icons and intermittent next turns; retest needed. |
| CarPlay · Kakao Navi | Current/next maneuvers and received distance; no-angle roundabout fallback | The 0.1.58 roundabout-output stop and missing upcoming slight-turn cases were reproduced from captured input and fixed in host tests. Vehicle confirmation is pending. |
| Android Auto · NAVER Map | Experimental current turn, trip distance, lane and ahead-turn callbacks | User reported missing next turn and unreliable trip distance; stopping/loading and text behavior need retest. |
| Android Auto · TMAP | Experimental current turn, trip distance, lane and ahead-turn callbacks | User reported missing next turn and TBT loading. Not reliable pending vehicle investigation. |
| Android Auto · Kakao Navi | Shared experimental receiver path | Not tested. |

### Known limitations

- No app-independent promise of next-turn availability: the navigation app must send the maneuver and distance callbacks. If absent, the mod does not fabricate them.
- Android Auto remains experimental. Ahead-turn reception, trip distance, recommended lanes and end-of-guidance cleanup are not yet confirmed across apps on the vehicle.
- A standalone rest-area icon has not been confirmed. Code 183 appeared as an exit diagram; 181 and 182 did not produce a confirmed standalone icon in the tested runs.
- Tollgates, rest areas, tunnels, overpasses and underpasses can be sent as generic turns. Dedicated artwork needs distinguishable source metadata; generic turns are not enough to infer the facility.
- Unprotected-left state, lane recommendations and pocket-lane markers require corresponding source or fresh compatible factory data.
- Only the 2023 Korean Palisade LX2 PE Gen5W running `LX2PE.KOR.0000.V039.001.260902` has been used for the development-vehicle checks. Other vehicles, regions, clusters and firmware are unverified, despite unrestricted installer metadata.

### Validation

Host regression tests, Android API 19 compilation and mod signature verification passed. The recorded-input Kakao and upcoming-turn regressions pass host tests. These checks do not verify cluster rendering. The latest changes have not yet been confirmed by a post-0.1.58 vehicle drive.

### Install

- Existing installation: copy `phone-projection-tbt-0.1.58.zip` to USB `HMGAPI/updates/`, remove earlier TBT update ZIPs, then use **Check USB updates → Apply changes → Restart Unit** while parked.
- First installation: install HMG-API 1.0.2 or later, then install the NEMESIS package attached to this release.
- The source archive is also attached. Do not publish vehicle logs or private signing material.

## 한국어

이번 릴리스는 CarPlay 회전 안내의 대체 처리를 개선하고, 이전 버전에서 추가한 시험용 Android Auto 수신부를 포함합니다. 아직 시험판이며 아래 앱별 동작 중 여러 항목은 실차 재검증이 필요합니다.

### 변경 사항

- 카카오내비 회전교차로의 CarPlay 안내가 교차로 플래그나 출구 각도 없이 들어와도 문구와 거리를 유지합니다. 각도가 없으면 방향을 추측하지 않고 비방향성 심볼을 사용합니다.
- 출구 각도가 있으면 교차로 플래그가 없어도 해당 각도에 맞는 회전교차로 그림을 선택합니다. 지원하지 않는 통행 방향은 비방향성으로 표시합니다.
- 현재 안내 그림 매핑을 다음 안내의 대체 경로로 사용합니다. 이전에 다음 안내 영역에서 사라지던 완만한 좌·우회전, 급회전, 도로 끝 회전을 처리합니다.
- 경로 시작 type 11에 방향 그림이 없어도 문구와 거리를 유지합니다.
- 이 릴리스 계열의 앞선 변경에는 CarPlay 시작 처리, CarPlay 차선 변환 수정, Android Auto 수신·수명주기 처리, 한글 도로명 복원, 화살표별 회색·흰색·파란색 표시가 포함됩니다. 버전별 내역은 `CHANGELOG.md`를 참고하세요.

### 앱·프로젝션별 지원과 상태

| 프로젝션 / 앱 | 구현된 기능 | 실차 상태와 미해결 문제 |
| --- | --- | --- |
| CarPlay · 네이버 지도 | 현재·다음 안내, 목적지 잔여 거리, 수신 차선 데이터 | 개발 차량에서 이전 시험을 했습니다. 0.1.58 시작·차선 변경은 재시험이 필요합니다. 포켓 차선은 일치하는 순정 데이터가 필요하고 휴게소 심볼은 미해결입니다. |
| CarPlay · TMAP | 현재·다음 안내, 잔여 거리, 차선 프레임 변환 | 0.1.55에서 기록된 직좌·직우 매핑을 수정했습니다. 주행 가능·추천 차선, 요금소·휴게소 그림, 다음 안내 간헐 누락 제보가 있어 재시험이 필요합니다. |
| CarPlay · 카카오내비 | 현재·다음 안내, 수신 거리, 각도가 없을 때 회전교차로 대체 처리 | 0.1.58에서 회전교차로 출력 중단과 다음 완만한 회전 누락을 기록 입력 및 호스트 검사로 재현·수정했습니다. 실차 확인은 남아 있습니다. |
| Android Auto · 네이버 지도 | 시험용 현재 안내, 목적지 잔여 거리, 차선·다음 안내 이벤트 수신 | 다음 안내 누락과 잔여 거리 불안정이 보고됐습니다. 종료 시 로딩과 한글 표시도 재시험해야 합니다. |
| Android Auto · TMAP | 시험용 현재 안내, 목적지 잔여 거리, 차선·다음 안내 이벤트 수신 | 다음 안내 누락과 TBT 로딩이 보고됐습니다. 추가 확인 전까지 안정 동작으로 볼 수 없습니다. |
| Android Auto · 카카오내비 | 공통 시험 수신 경로 | 시험하지 않았습니다. |

### 알려진 한계

- 다음 안내는 지도 앱이 다음 회전과 거리를 실제로 보내야 표시됩니다. 이벤트가 없으면 모드가 값을 만들어내지 않습니다.
- Android Auto는 시험 기능입니다. 다음 안내, 목적지 거리, 추천 차선, 안내 종료 처리를 차량에서 앱별로 확인하지 못했습니다.
- 휴게소 단독 심볼은 확인되지 않았습니다. 183은 진출 도식으로 나왔고, 181·182는 시험 주행에서 단독 그림이 확인되지 않았습니다.
- 요금소·휴게소·터널·고가·지하차도가 일반 회전으로만 전달될 수 있습니다. 전용 그림에는 구분 가능한 원본 정보가 필요하며 일반 회전만으로 시설을 추정하지 않습니다.
- 비보호 좌회전, 차선 추천, 포켓 차선은 지도 앱 또는 최신 순정 데이터가 해당 정보를 제공해야 합니다.
- 개발 차량 시험은 대한민국 2023년식 팰리세이드 LX2 PE Gen5W, `LX2PE.KOR.0000.V039.001.260902`에서만 했습니다. 설치 제한이 없더라도 다른 차량·국가·계기판·펌웨어는 미확인입니다.

### 검증

호스트 회귀 검사, Android API 19 컴파일, 모드 서명 검증을 통과했습니다. 기록 입력을 사용한 카카오 회전교차로와 다음 안내 회귀 검사도 통과했지만, 이는 계기판 그림 출력의 확인이 아닙니다. 0.1.58 적용 후 실차 주행 결과는 아직 없습니다.

### 설치

- 기존 설치: `phone-projection-tbt-0.1.58.zip`을 USB의 `HMGAPI/updates/`에 복사하고 이전 TBT 업데이트 ZIP을 제거한 다음, 정차 상태에서 **Check USB updates → Apply changes → Restart Unit**을 실행합니다.
- 최초 설치: HMG-API 1.0.2 이상을 먼저 설치하고 이 릴리스의 NEMESIS 패키지를 설치합니다.
- 소스 ZIP도 첨부했습니다. 차량 로그와 개인 서명 자료는 공개하지 마세요.
