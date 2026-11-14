# Changelog / 변경 내역

## 0.1.58

- Keep CarPlay roundabout entry/exit text and distance live when maneuver types 6/7 omit junction metadata or exit angle. Missing angle uses non-directional type 64, with explicit roundabout text; no exit bearing is invented.
- An explicitly received roundabout exit angle can select a roundabout icon even if the junction flag is absent. Unhandled left-hand-traffic geometry remains non-directional.
- Use the current-maneuver mapping as the ahead-icon fallback, fixing slight-left/right 49/50, sharp turns and end-of-road turns disappearing from the upcoming slot.
- Route-start type 11 also retains text/distance instead of aborting output.
- 카카오내비 회전교차로 진입에서 출력이 멈추는 경로와 다음 안내의 완만한 좌·우회전 변환 누락을 보완했습니다. 각도가 없는 회전교차로는 문구·거리만 유지합니다.
- Recorded-input and startup host regressions pass. Vehicle rendering is not yet validated. Exact underpass artwork still needs received facility text or other verified facility metadata.

This release is a prerelease. The Kakao roundabout and upcoming-turn fallbacks were reproduced from captured CarPlay data, but neither has a post-0.1.58 vehicle confirmation. See [the full release notes](RELEASE-v0.1.58.md) for the CarPlay/Android Auto app matrix, known issues, installation and validation status.

## 0.1.57

- Allow received contiguous upcoming CarPlay maneuvers before the optional source name arrives; preserve active-state, list-count and received-maneuver checks.
- Use an explicitly received current-maneuver distance while waiting for the route-level countdown. Missing distance still waits instead of rendering a fabricated 0.0 km.
- Track maneuver-distance validity and reset it with maneuver state.
- CarPlay 첫 안내의 앱 이름 지연과 초기 거리 대기로 인한 표시 누락을 보완했습니다. 실제 수신 거리만 사용합니다.
- Include Kakao Navi in the received contiguous-maneuver selection: recorded input at 08:32:07 includes upcoming index 1 and its 1600 m distance.
- Seventeen startup regression assertions pass, including the recorded first-route snapshot and Naver's 65 m initial maneuver. Vehicle validation remains pending; absent AA ahead/lane data remains unresolved.

## 0.1.56

- Replay complete Android Auto current-turn and trip data received before active status when guidance becomes active.
- Clear cached route state after navigation ends; distance-only callbacks cannot resurrect the previous turn.
- Add host tests for first-activation ordering, repeated status, and restarting without stale route data.
- AA 시작 전 수신한 안내의 표시 누락을 보완했습니다. 실차 검증은 남아 있습니다.
- Missing AA ahead maneuvers/recommended lanes remain unresolved: latest callbacks contain no ahead events and empty lane data. This release does not fabricate either feature.
- CarPlay first-route source-name gating and initial-distance availability remain separate open issues.

## 0.1.55

- Merge general and highlighted CarPlay lane directions instead of assuming highlighted directions are already in the general list.
- Fix recorded TMAP straight/left and straight/right lanes losing the straight arrow and becoming entirely gray.
- TMAP의 일반 방향과 강조 방향을 합쳐 직좌·직우 복합 차선과 방향별 강조를 보존합니다.
- Pocket markers still require fresh, compatible factory lane hints. No such factory frames were observed during the latest TMAP drive; this release does not fabricate pocket markers.
- Recorded-input host regressions pass; vehicle display validation remains pending.

## 0.1.54

- Fix service-area selection being applied only to the main TBT call. Current, HUD and ahead mappings now use the same selected code.
- Default service-area code is183.
- 본문183·안내 목록181로 불일치하던 회귀 오류를 수정했습니다. 실차 재검증이 필요합니다.

## 0.1.53

- Default CarPlay service-area output is now 182 with baseline auxiliary arguments.
- Added a 183 fallback in the new service_area_display setting. Existing diagnostic selections do not override this new setting.
- 휴게소는 우선182로 시험하고, 미출력 또는 졸음쉼터 그림이면183으로 전환할 수 있습니다.

## 0.1.52

- Added controlled CarPlay service-area argument profiles for codes181/182/183.
- Default test181_aux uses sideStreet65535,direction255,bar15,error-2. These are experimental hypotheses, not a recovered native Java call.
- Capture all nine native TBT API arguments and Binder-proxy transport arguments, with facility text for correlation.
- Added profile range/fallback checks. Vehicle rendering and the auxiliary-argument hypothesis remain unverified.
- 휴게소 추가 인자 비교와 순정 API/Binder 인자9개 진단을 추가했습니다. 실제 그림 표시 여부는 미검증입니다.

## 0.1.51

- Recover CarPlay TMAP lane geometry when only highlighted directions are supplied.
- Restore Android Auto road text from the intact UTF-8 NavigationState packet.
- Remove the failed service-area type 13 trial that opened the junction display.
- Recognize TG suffixes and use domestic TG type 184. Add dump-derived tunnel type 176.
- Extend named overpass/underpass side-road handling to received maneuver types 49/50.
- All changes require vehicle verification. Standalone service-area artwork, Android Auto missing next turns/lanes, and pocket-lane availability remain unresolved.
- TMAP 화살표 누락과 Android Auto 끝글자 손상을 보완하고, 휴게소 유형 13 시험을 제거했습니다. TG·터널·옆길 처리를 추가했습니다. 전체 요구사항 해결은 미완료입니다.

## 0.1.50

- Added a CarPlay service-area cluster type 13 trial, enabled by default and switchable in settings.
- Based on the native SetCaseServiceArea legacy path. The physical cluster symbol is unverified. Native progress and all legacy parameter semantics are not reconstructed.
- HUD and next-turn symbols keep their existing modern encoding.
- CarPlay 휴게소에 기존 방식 유형 13을 시험하는 설정을 추가했습니다. 끄면 유형 181로 복귀합니다. 실차 표시와 기존 방식의 전체 인자 재현은 미확인입니다.

## 0.1.49

- Android Auto ends with cluster type 8, matching the observed LX2 PE native termination sequence.
- Added display protection leases to Android Auto and periodic remaining-distance refresh while the lease integration is active.
- CarPlay TMAP can use the received adjacent maneuver as the next-turn candidate, following its observed contiguous snapshots.
- Preserve supplied lane geometry when all received lane directions are unavailable. Recommendations continue to use received status.
- Added raw Android Auto callback text, lane payloads and native navigation dumps to mod diagnostics.
- Android Auto 종료 로딩, 남은 거리 덮어쓰기, TMAP 다음 안내 및 차선 숨김 처리를 보완했습니다.
- Korean text rendering, service-area rendering, Android Auto TMAP loading and app-specific missing fields remain under diagnosis. Vehicle verification is pending.

## 0.1.48

- Added Android Auto navigation callbacks and the HMG-API AaTurns conversion implementation.
- Added ENUM cluster capability selection, two verified native receiver recipes, and route/connection lifecycle hooks.
- Added an Android Auto TBT toggle, enabled by default.
- Android Auto 길안내 수신·계기판 변환 및 연결 수명주기 처리를 추가했습니다.
- Android Auto는 시험 구현이며 실차 동작 확인이 필요합니다. 휴게소 표시 및 앱별 미해결 증상은 남아 있습니다.
- Third-party attribution is recorded in NOTICE.md.

## 0.1.47

- Wait for a received current-turn distance instead of sending an uninitialized zero. Use the received numeric meter value when the formatted distance is unavailable.
- Refresh the next-maneuver bound when the route count arrives separately from the maneuver list.
- Handle straight maneuver type 3 at a roundabout when a valid exit angle is received.
- Preserve route, trip, source-change, and native-fix records in exported history.
- Initial fixes for reported symptoms. Remaining-distance loss, intermittent pocket markers, service-area rendering, and TMAP/Kakao guidance still require diagnosis. Android Auto TBT remains unimplemented.
- 초기 거리 0 표시와 늦게 도착하는 다음 안내 개수 처리를 보완하고, 출구 각도가 있는 회전교차로 type 3 처리를 추가했습니다.
- 전체 증상 해결 및 실차 검증은 미완료입니다. Android Auto TBT는 미구현입니다.

## 0.1.46

- Removed model, region, and firmware restrictions from both the HMG-API update and NEMESIS installer.
- Restricted the native lane fixes to the verified iAP2 library build. CarPlay guidance continues through the existing HMG-API hooks.
- HMG-API 업데이트와 NEMESIS 최초 설치의 차종·국가·펌웨어 제한을 해제했습니다.
- 검증된 순정 iAP2 라이브러리에서만 네이티브 차선 보정을 적용합니다. CarPlay 길안내는 기존 HMG-API 훅을 사용합니다.

## English overview · 0.1.45

Phone Projection TBT is the new project/mod name. CarPlay uses a shared wired/wireless receiver; Android Auto TBT is planned and is not implemented. Existing ID and signer remain compatible. New log name: `phone-projection-tbt.log`.

Since public 0.1.41: corrected composite lane shapes and per-direction colors, added guarded factory pocket markers, updated highway facility mappings, improved USB diagnostics, and introduced an unverified alternative service-area icon. Overpass/underpass and unprotected-turn attributes are not guessed when absent. Latest rendering changes remain under vehicle verification. See the bilingual [0.1.45 release notes](RELEASE-v0.1.45.md).

## 0.1.45 · 2026-11-09 · Phone Projection TBT

- 프로젝트·모드 표시 이름과 패키지 파일명을 Phone Projection TBT로 변경했습니다.
- 유선·무선 CarPlay를 포함하는 설명으로 정리하고 Android Auto TBT는 지원 예정으로 명시했습니다.
- 진입 클래스와 Java 네임스페이스를 projection / projection.carplay로 정리했습니다.
- 새 USB 로그 이름은 `phone-projection-tbt.log`입니다.
- 기존 설치의 모드 ID, 서명자와 설정 키는 유지합니다.
- 0.1.44의 차선·시설 표시 동작을 유지합니다.

## 0.1.42–0.1.44 · 로컬 시험 빌드

- 순정 리소스와 주행 로그를 기준으로 차선별 색상 및 복합 화살표를 수정했습니다.
- 좌회전·유턴 차선은 같은 주행에서 확보한 순정 조합을 적용했습니다.
- 최신 순정 데이터의 차선 수·방향이 일치할 때 포켓 차선 표시를 보완합니다.
- 휴게소 진출 도식 대신 대체 순정 코드 181을 시험 적용합니다. 단독 심볼인지는 실차 미확인입니다.
- 구분 정보가 없는 고가·지하차도와 비보호 좌회전은 임의로 추정하지 않습니다.
- USB 로그 수집에서 차량에 없는 tail 명령 의존성을 제거했습니다.

## 0.1.41 · 2026-11-08 · 시험판

- 차선의 각 화살표에 2비트 표시 상태를 배정합니다. 회색은 경로에 맞지 않는 방향, 흰색은 주행 가능한 방향, 파란색은 추천 방향입니다. 복합 차선에서 일부 화살표가 사라지거나 잘못 강조되던 처리를 수정했습니다.
- 차선 안내가 계속 유효한 동안 표시와 점유 시간을 갱신해, 최초 표시 후 12초가 지나 사라지던 문제를 수정했습니다.
- 계기판에서 그림이 비어 있던 좌측 고속도로 진입 코드는 표시 가능한 좌측 분기 화살표로 대체합니다.
- 고가차도 진입 및 우측 옆길 문구에 대한 시험 매핑을 포함합니다. 고가차도·옆길 전용 그림은 실차 확인이 필요합니다.
- 순정 내비의 계기판 전송 데이터 및 내부 전달 함수 5종을 관찰합니다. 회전하는 진단 로그를 보관하고 USB 연결 시 기존 TBT 로그와 함께 `stock-guidance.log`로 내보냅니다.

실제 수신 로그의 차선 상태·복합 방향 재현 검사, 컴파일, USB 로그 저장 검사와 서명 검증을 통과했습니다. 0.1.41의 실제 계기판 색상과 표시 지속 여부는 재확인 중입니다.

휴게소 전용 심볼 표시는 미해결입니다. 추가한 내부 전달 함수에서 휴게소 심볼 식별값을 얻을 수 있는지도 확인 중입니다.
