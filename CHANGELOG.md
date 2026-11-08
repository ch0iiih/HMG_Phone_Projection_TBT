# Changelog / 변경 내역

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
