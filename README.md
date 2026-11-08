# HMG Phone Projection TBT

폰 프로젝션의 길안내를 계기판과 HUD에 표시하는 HMG-API 커뮤니티 모드입니다. 현재·다음 회전, 목적지까지 남은 거리, 내비 앱이 보내는 추천 차선을 표시합니다.

## 지원 차량과 국가

| 국가 | 차량 | 내비게이션 펌웨어 | 상태 |
| --- | --- | --- | --- |
| 대한민국 (KOR) | 현대 팰리세이드 2023년식 LX2 PE, 순정 Gen5W | `LX2PE.KOR.0000.V039.001.260902` | CarPlay 및 네이버 지도에서 실차 시험 |

일반 안내와 회전교차로는 이전 버전에서 실차 확인했습니다. 현재 공개 시험판은 **0.1.41**입니다. 차선별 화살표에 회색(경로에 맞지 않는 방향), 흰색(주행 가능한 방향), 파란색(추천 방향)을 나누어 보내도록 수정했습니다. 실제 수신 로그를 이용한 검증과 패키지 서명 검증을 통과했으며, 0.1.41의 실차 표시는 재확인 중입니다. 좌측 고속도로 진입 안내는 표시 가능한 좌측 분기 화살표로 대체합니다. 고가차도·옆길의 일부 그림 번호는 시험 적용 중이고, 휴게소 전용 그림 표시는 미해결입니다. 다른 국가·차종·펌웨어의 호환성은 미확인입니다.

## 설치

1. 차량에 [HMG-API](https://github.com/VysseCodes/HMG-API) 1.0.2 이상을 설치하고, 앱에서 해당 차량의 맵이 검사되어 **Hooks on (map checked)** 상태인지 확인합니다.
2. [0.1.41 시험판 설치 ZIP](https://github.com/ch0iiih/HMG_Phone_Projection_TBT/raw/refs/heads/main/packages/wired-carplay-tbt-lx2pe-0.1.41.zip)을 받아 USB의 `HMGAPI/updates/`에 복사합니다. ZIP은 압축을 푼 상태로 설치할 수 없습니다.
3. 정차 상태에서 USB를 차량에 연결하고 HMG-API 앱의 **Check USB updates → Apply changes → Restart Unit**을 실행합니다. 처음 설치할 때 서명자 확인 화면이 나오면 아래 UUID와 대조합니다.
4. iPhone을 AVN에 데이터 케이블로 연결하고 길안내를 시작합니다. 네이버 지도에서는 CarPlay의 HUD 연동을 켜고, 계기판에서 길안내 화면을 선택합니다.
5. 되돌리려면 HMG-API **Mods**에서 이 모드를 끄거나 제거하고 변경 사항을 적용한 뒤 AVN을 재시작합니다.

이전 공개 버전은 [릴리스](https://github.com/ch0iiih/HMG_Phone_Projection_TBT/releases)에서 받을 수 있습니다. 0.1.41 변경 내역은 [CHANGELOG.md](CHANGELOG.md)에 있습니다.

릴리스 서명자 UUID `9c567984-70b6-83c8-a27d-10230586e0df`

## 차종별 제보

[GitHub Issues](https://github.com/ch0iiih/HMG_Phone_Projection_TBT/issues/new)에 제목을 `[국가 / 차종 코드 / 펌웨어] 증상`으로 작성해 주세요. 현재 지원 범위 밖의 차량은 모델과 펌웨어를 먼저 제보해 주세요.

- 국가, 차종·연식, 차량 코드, AVN 소프트웨어 전체 버전
- HMG-API·모드 버전, iOS·지도 앱 버전, iPhone 직접 연결 또는 어댑터 여부
- 기대한 표시와 실제 표시, 재현 순서, 대략적인 발생 시각
- USB 루트에 빈 `TBT-LOGS` 폴더를 만든 뒤 정차해서 차량에 꽂아 주세요. `TBT-LOGS/wired-carplay-tbt.log`와 순정 안내 수집 파일인 `TBT-LOGS/stock-guidance.log`에서 관련 부분을 첨부할 수 있습니다. 로그는 USB를 차량에 다시 연결할 때 저장됩니다. 0.1.41은 진단용으로 내비 내부 전달 데이터와 안내 문구도 기록하며, 회전하는 로그 파일에 최근 기록을 보관합니다. 공개 전에 차대번호, 주소, 기기 식별자 등 개인 정보를 가려 주세요.

## 개발 및 라이선스

[빌드 방법](BUILD.md)을 참고하세요. 차량 전용 변경과 가드 소스는 `src/`와 `native/`에 있습니다.

[GNU AGPL-3.0](LICENSE). [HMG-API](https://github.com/VysseCodes/HMG-API)의 Wireless CarPlay + Android Auto 모드에서 길안내 코드를 가져와 유선 CarPlay와 대상 차량에 맞게 수정했습니다.

