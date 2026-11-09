# Pleos API 실측 기록

에뮬레이터: Pleos Connect v2.1.0 / Vehicle SDK 2.3.2. `tools/api-explorer`로 하나씩 눌러보고 Virtual Console 값과 비교한다.
제어는 debug 빌드 데모 명령 `probe`로 대상마다 "원래 값 읽기 → 바꾸기 → 1초 → 다시 읽기 → 되돌리기 → 1초 → 다시 읽기"를 하고, 같은 순간의 콘솔 값을 함께 남긴다(원본 CSV: 처음 `relay/files/20261109-1656-제어실측.csv`, area 수정 후 `relay/files/20261109-1840-제어실측-수정후.csv`).
앱이 다시 읽은 값과 콘솔 값이 다른 행은 메모에 **불일치**로 표시한다.
결과: `반영` · `읽기값만` (다시 읽은 값만 바뀜·지연) · `실패` (실패·API 없음)

Virtual Console은 브라우저 세션이 열려 있을 때만 차량 쪽과 연결된다. 페이지를 열어 두거나
`POST /browser/session?client=<이름>`을 1초마다 보낸다(3초 끊기면 `emulator_inactive`).
읽기 판정 근거: Pleos 서비스 로그 `broadcastPropertyCallback(applicationId = com.westone.sunstone …)`와 앱 상태 줄.

| 루틴 | API | 우리 코드 호출 방식 | 결과 | 메모 |
|---|---|---|---|---|
| 세차 | getCurrentWindowPosition / setWindowPosition (ROW_1_LEFT…ROW_2_RIGHT) | 직접 | 반영 | 2026-11-09 probe 측정. 4개 모두 0→5→0, 앱 다시 읽기 5/0, 콘솔 50/0(%, 앱 값 ×10). 바꾸기·되돌리기 각 1.5초(확인 대기 포함). 다른 팀 사례(0으로 되돌리기 실패)는 재현 안 됨 |
| 세차 | setWindowPosition ROOF_TOP_1/2 (선루프) | 직접 | 실패 | 2026-11-09 probe 측정. 읽기·쓰기 모두 `area ID: 0x10000/0x20000 not supported for property ID: WINDOW_POS`. 에뮬레이터에 선루프 없음. 콘솔에도 항목 없음 |
| 세차 | getCurrentWiperSwitch / setWiperSwitch (OFF·AUTO) | 직접 | 반영 | 2026-11-09 probe 측정. OFF→AUTO→OFF, 앱 AUTO/OFF, 콘솔 Auto/Off. 0.4초. 리플렉션 `setWiperSwitch` 존재 확인 |
| 세차 | getHvacRecirculationState / setHvacRecirculation | 직접 | 반영 | 2026-11-09 수정 후 probe. 다섯 좌석 합친 area(117)로 부르니 false→true→false, 앱·콘솔(`hvac.recirc_on`) 일치, 0.4초. 처음엔 ROW_1_LEFT(0x1)로 불러 `not supported` 실패 |
| 세차·차일드락 | getDoorLockState / setDoorLock (네 문 합친 area 85) | 직접 | 반영 | 2026-11-09 수정 후 probe. 문 전체 잠금 하나로 다룬다. true→false→true, 앱·콘솔(`body.door_locks.all`) 일치, 0.4초. 처음엔 문마다 불러 `area ID: 0x1 not supported for property ID: DOOR_LOCK` 실패 |
| 차일드락 | getCurrentWindowLockState / setWindowLock (ROW_2_*) | 직접 | 읽기값만 | 2026-11-09 probe 측정(수정 후 같음). **불일치**: 뒷좌석 2개 false→true→false, 앱 다시 읽기 true/false, 콘솔은 계속 false. 다른 팀 사례(읽기값만 true, 콘솔 false)와 같다. 그래서 결과를 Done이 아닌 Sent로 돌려준다. 앞좌석은 따로 잠글 area가 없어(네 창문 합친 area만) 보내기 전에 Unsupported |
| 차일드락 | moveWindowBlind (REAR_WINDSHIELD) / getCurrentWindowBlindPosition | 직접 | 실패 | 2026-11-09 probe 측정. `area ID: 0x2 not supported for property ID: 0x23400532`. 지원 area는 옆 창문 네 곳(ROW_1/2 LEFT/RIGHT)뿐이고 뒷유리는 없다. 수정 후에는 지원 목록을 먼저 보고 Unsupported로 안내. 콘솔에도 항목 없음 |
| 차일드락 | getHvacPowerState / setHvacPower (다섯 좌석 합친 area 117) | 직접 | 반영 | 2026-11-09 수정 후 probe. 좌석별 전원은 없고 차 전체 전원 하나다. 어느 좌석 요청이든 전체 전원으로 부르면 false→true→false, 앱·콘솔(`hvac.power_on`) 일치, 0.4초. 처음엔 좌석마다 불러 `not supported` 실패. "뒷좌석 공조 켜기"가 차 전체 공조를 켠다 |
| 차일드락 | getCurrentSeatOccupancy / registerSeatOccupancy | 직접 | 실패 | 2026-11-09 측정. 값은 오지만 반대로 읽힌다. 에뮬레이터는 AAOS 기준 1=빈자리, 2=탑승(`SEAT_OCCUPANCY` area 1/4/16/64)을 보내는데 SDK 2.3.2는 `값 == 1`을 탑승(true)으로 바꾼다. 운전석·조수석·뒷좌석 좌우 4곳 모두 콘솔 탑승→앱 빈자리, 콘솔 빈자리→앱 탑승. 뒷좌석 가운데는 콘솔에 없고 앱은 늘 탑승. 2026-11-09 `PleosCarPort.INVERT_OCCUPANCY`로 뒤집고 가운데는 읽지 않게 바꾼 뒤, 콘솔 4자리 탑승/빈자리가 앱 상태 줄과 일치 |
| 차일드락 | getCurrentSeatBeltBuckledState / registerSeatBeltBuckledState (ROW_2_*) | 직접 | | 2026-11-09 확인 못 함. Virtual Console 카탈로그에 좌석별 벨트 항목이 없다 |
| 시트 | getCurrentSeat{ForeAft,Height,BackrestAngle,Tilt,Depth,HeadrestHeight}Position | 직접 | 실패 | 2026-11-09 probe 측정. **불일치**: 앞뒤·높이·등받이·쿠션 각도·쿠션 길이는 함수가 있으나 늘 0을 돌려준다(콘솔 100일 때도 0). 머리받이 읽기 함수는 SDK 이름이 `getCurrentSeatHeadrestHeightAftPosition`이라 리플렉션이 못 찾았다. 직접 호출로 바꾼 뒤에도 늘 0 |
| 시트 | moveSeat{…}(area, FORWARD/BACKWARD/STOP) | 직접 | 반영 | 2026-11-09 probe 측정. 앞뒤·높이·등받이·쿠션 각도 모두 FORWARD로 콘솔 값이 0→100 증가. 읽기가 0에 고정돼 목표에서 못 멈추고 10초 제한 후 STOP. 되돌리기도 "이미 0"으로 판단해 움직이지 않아 콘솔 API로 0에 복구했다. 쿠션 길이는 콘솔에 항목 없음 |
| 시트 | selectSeatMemory(area, n) | 직접 | | 2026-11-09 probe 측정. 1번 호출, 실패 콜백 없이 1초 후 Sent. 콘솔 앞뒤 위치 변화 없음(100→100). 메모리에 저장된 값이 없어서인지 판단 못 함 |
| 공통 | getGear().registerGearSelection | 직접 | 반영 | 2026-11-09 측정. P→D→R→P 모두 전달(`VENDOR_GEAR_SELECTION` 4/8/2/4), 앱 상태 PARK/DRIVE/PARK 확인. R에서는 후방 카메라가 앱을 가려 화면 확인은 못 함. deprecated, GEAR_SELECTION 속성 권장 – 실측 때 비교 |
| 공통 | getCurrentDoorPosition / registerDoorPosition | 직접 | 반영 | 2026-11-09 측정. ROW_2_LEFT/RIGHT 열기·닫기 모두 전달(`DOOR_POS` area 16/64, 1/0), 앱 상태 줄 일치 |
| 공통 | PERF_VEHICLE_SPEED (AAOS) | 직접 | | 에뮬레이터에서 못 넣으면 데모 `speed` |

## 지원 area (2026-11-09, 데모 `areas`)

SDK `check…Capabilities`가 돌려준 값. 괄호 안 번호는 Pleos 서비스 로그의 `areaIds`와 같다(좌석·문·창문마다 번호 체계가 다르다).

| API | 권한 | 지원 area | 메모 |
|---|---|---|---|
| HvacRecirculation | READ_WRITE | 다섯 좌석 합친 하나(117) | 좌석별 없음 |
| HvacPower | READ_WRITE | 다섯 좌석 합친 하나(117) | 좌석별 없음 |
| DoorLock | READ_WRITE | 보닛(268435456), 네 문 합친 하나(85) | 문별 없음 |
| MoveWindowBlind / WindowBlindPosition | READ_WRITE | 옆 창문 ROW_1/2 LEFT/RIGHT(16/64/256/1024), 0~10 | 뒷유리 없음 |
| WindowLock | READ_WRITE | 네 창문 합친 하나(1360), ROW_2_LEFT(256), ROW_2_RIGHT(1024) | 앞좌석 따로 없음 |
| WindowPosition | READ_WRITE | ROW_1/2 LEFT/RIGHT, 0~10 | 선루프 없음 |
| WiperSwitch | READ_WRITE | 앞유리(1)·뒷유리(2): OFF, CONTINUOUS_LEVEL_1, CONTINUOUS_LEVEL_3, AUTO | MIST·INTERMITTENT 없음 |
| SeatOccupancy | READ | 운전석·조수석·뒷좌석 좌·가운데·우(1/4/16/32/64) | 콘솔엔 가운데 없음 |
| SeatBeltBuckled | READ_WRITE | 위와 같은 5자리 | 콘솔엔 벨트 없음 |
| SeatForeAftPosition | READ_WRITE | ROW_1/2 LEFT/RIGHT, 0~100 | 읽으면 늘 0 |
| MoveSeatForeAft | READ_WRITE | ROW_1/2 LEFT/RIGHT | |
| SeatMemory | WRITE | 운전석(1), 1~2번 | 읽기 없음 |

