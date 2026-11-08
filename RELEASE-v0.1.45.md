# HMG Phone Projection TBT 0.1.45 · Prerelease

## English

Renamed the project and mod to Phone Projection TBT for wired/wireless CarPlay and planned Android Auto support. **Android Auto TBT is not implemented in this release.** Existing installations keep the same internal mod ID and signer.

Changes since the previous public 0.1.41 prerelease:

- Corrected per-direction gray/white/blue lane states and composite lane arrows against factory resources and captured driving data.
- Corrected the left-turn/U-turn composite using the matching factory frame.
- Added factory pocket-lane markers only when fresh lane count and directions are compatible; preserves phone arrows and recommendation colors.
- Updated highway entry/exit mappings from factory conversion code.
- Replaced the physically unsuitable service-area exit code 183 with alternative native code 181 for testing. **A standalone service-area icon is not yet confirmed.**
- Removed the USB export dependency on the unavailable `tail` command and expanded diagnostic capture.
- Renamed new log exports to `phone-projection-tbt.log` and added separate English/Korean documentation.

Generic phone maneuvers cannot reliably distinguish overpass/underpass/side roads or unprotected left turns when those attributes are absent. The latest display changes still require vehicle verification.

Validation: 1,801 checks, a 42-packet drive replay, Android API 19 compilation, and author-signature verification. These are not substitutes for a vehicle rendering test.

Assets: use the regular ZIP for an existing HMG-API TBT installation; use the NEMESIS installer ZIP for first installation with HMG-API already installed. Source ZIP and SHA-256 checksums are included. Apply updates while parked and restart the unit.

## 한국어

유선·무선 CarPlay와 향후 Android Auto 지원을 포괄하도록 프로젝트와 모드 이름을 Phone Projection TBT로 변경했습니다. **이번 버전의 Android Auto TBT는 미구현**입니다. 기존 설치의 내부 ID와 서명자는 유지합니다.

이전 공개 시험판 0.1.41 이후 변경:

- 순정 리소스와 주행 로그를 기준으로 차선별 회색·흰색·파란색 상태와 복합 화살표를 수정했습니다.
- 좌회전·유턴 차선은 같은 주행에서 확보한 순정 조합을 적용했습니다.
- 최신 순정 데이터의 차선 수·방향이 맞을 때만 포켓 차선 표시를 보완하며, 폰의 화살표와 추천 색상은 유지합니다.
- 순정 변환 코드를 기준으로 고속도로 진입·진출 그림 매핑을 수정했습니다.
- 휴게소 진출 도식으로 확인된 183 대신 대체 순정 코드 181을 시험 적용합니다. **단독 휴게소 심볼인지는 미확인**입니다.
- USB 로그 내보내기에서 차량에 없는 `tail` 명령 의존성을 제거하고 진단 항목을 보강했습니다.
- 새 로그 이름을 `phone-projection-tbt.log`로 변경하고 README를 영어·한국어로 분리했습니다.

수신 속성이 없는 고가·지하차도·옆길 및 비보호 좌회전을 일반 회전 정보만으로 구분하지 않습니다. 최신 표시 변경은 실차 검증이 남아 있습니다.

검증: 검사 1,801건, 주행 패킷 42개 재현, Android API 19 컴파일, 서명 검증. 실차 표시 검증 완료를 의미하지 않습니다.

기존 설치는 일반 업데이트 ZIP, 최초 설치는 HMG-API 설치 후 NEMESIS 설치 ZIP을 사용하세요. 소스 ZIP과 SHA-256 체크섬을 함께 제공합니다. 정차 상태에서 적용하고 AVN을 재시작하세요.
