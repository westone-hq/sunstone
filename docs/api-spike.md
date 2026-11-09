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
| 세차 | setWindowPosition ROOF_TOP_1/2 (선루프) | 직접 | 실패 | 2026-11-09 probe 측정. 읽기·쓰기 모두 `area ID: 0x10000/0x20000 not supported for property ID: WINDOW_POS`. 에뮬레이터에 선루프 없음. 콘솔에도 항목 없음. 2026-11-09 car/seat-timing: `checkWindowPositionCapabilities`를 먼저 보고 Unsupported로 안내, 실행 기록에 "이 차에서는 지원하지 않아요" |
| 세차 | getCurrentWiperSwitch / setWiperSwitch (OFF·AUTO) | 직접 | 반영 | 2026-11-09 probe 측정. OFF→AUTO→OFF, 앱 AUTO/OFF, 콘솔 Auto/Off. 0.4초. 리플렉션 `setWiperSwitch` 존재 확인 |
| 세차 | getHvacRecirculationState / setHvacRecirculation | 직접 | 반영 | 2026-11-09 수정 후 probe. 다섯 좌석 합친 area(117)로 부르니 false→true→false, 앱·콘솔(`hvac.recirc_on`) 일치, 0.4초. 처음엔 ROW_1_LEFT(0x1)로 불러 `not supported` 실패 |
| 세차·차일드락 | getDoorLockState / setDoorLock (네 문 합친 area 85) | 직접 | 반영 | 2026-11-09 수정 후 probe. 문 전체 잠금 하나로 다룬다. true→false→true, 앱·콘솔(`body.door_locks.all`) 일치, 0.4초. 처음엔 문마다 불러 `area ID: 0x1 not supported for property ID: DOOR_LOCK` 실패 |
| 차일드락 | getCurrentWindowLockState / setWindowLock (ROW_2_*) | 직접 | 읽기값만 | 2026-11-09 probe 측정(수정 후 같음). **불일치**: 뒷좌석 2개 false→true→false, 앱 다시 읽기 true/false, 콘솔은 계속 false. 다른 팀 사례(읽기값만 true, 콘솔 false)와 같다. 2026-11-09 car/upgrade: SDK 다시 읽기는 반영되고 콘솔만 불일치(에뮬레이터 쪽으로 보임)라 다시 읽은 값으로 판단해 잠김이면 Done. 차일드락 루틴에서 뒷창 좌우 Done 확인. 실차 미확인. 앞좌석은 따로 잠글 area가 없어(네 창문 합친 area만) 보내기 전에 Unsupported |
| 차일드락 | moveWindowBlind (REAR_WINDSHIELD) / getCurrentWindowBlindPosition | 직접 | 실패 | 2026-11-09 probe 측정. `area ID: 0x2 not supported for property ID: 0x23400532`. 지원 area는 옆 창문 네 곳(ROW_1/2 LEFT/RIGHT)뿐이고 뒷유리는 없다. 수정 후에는 지원 목록을 먼저 보고 Unsupported로 안내. 콘솔에도 항목 없음 |
| 차일드락 | getHvacPowerState / setHvacPower (다섯 좌석 합친 area 117) | 직접 | 반영 | 2026-11-09 수정 후 probe. 좌석별 전원은 없고 차 전체 전원 하나다. 어느 좌석 요청이든 전체 전원으로 부르면 false→true→false, 앱·콘솔(`hvac.power_on`) 일치, 0.4초. 처음엔 좌석마다 불러 `not supported` 실패. "뒷좌석 공조 켜기"가 차 전체 공조를 켠다 |
| 차일드락 | getCurrentSeatOccupancy / registerSeatOccupancy | 직접 | 실패 | 2026-11-09 측정. 값은 오지만 반대로 읽힌다. 에뮬레이터는 AAOS 기준 1=빈자리, 2=탑승(`SEAT_OCCUPANCY` area 1/4/16/64)을 보내는데 SDK 2.3.2는 `값 == 1`을 탑승(true)으로 바꾼다. 운전석·조수석·뒷좌석 좌우 4곳 모두 콘솔 탑승→앱 빈자리, 콘솔 빈자리→앱 탑승. 뒷좌석 가운데는 콘솔에 없고 앱은 늘 탑승. 2026-11-09 `PleosCarPort.INVERT_OCCUPANCY`로 뒤집고 가운데는 읽지 않게 바꾼 뒤, 콘솔 4자리 탑승/빈자리가 앱 상태 줄과 일치. car/upgrade부터 `Build.HARDWARE`가 에뮬레이터(`ranchu`)일 때만 뒤집는다. 다른 팀도 에뮬레이터에서 같은 현상. 실차 미확인 |
| 차일드락 | getCurrentSeatBeltBuckledState / registerSeatBeltBuckledState (ROW_2_*) | 직접 | | 2026-11-09 확인 못 함. Virtual Console 카탈로그에 좌석별 벨트 항목이 없다 |
| 시트 | getCurrentSeat{ForeAft,Height,BackrestAngle,Tilt,Depth,HeadrestHeight}Position | 직접 | 실패 | 2026-11-09 probe 측정. **불일치**: 앞뒤·높이·등받이·쿠션 각도·쿠션 길이는 함수가 있으나 늘 0을 돌려준다(콘솔 100일 때도 0). 머리받이 읽기 함수는 SDK 이름이 `getCurrentSeatHeadrestHeightAftPosition`이라 리플렉션이 못 찾았다. 직접 호출로 바꾼 뒤에도 늘 0. 2026-11-09 데모 `seatcheck`: `check…PositionCapabilities`는 앞뒤·높이·등받이·쿠션 각도 READ_WRITE(1/4/16/64, 0~100), 머리받이 READ_WRITE(앞좌석, -10~10)로 지원한다고 나온다. 그러나 지원 목록이 준 area 그대로 읽어도 0(콘솔 40), 위치 리스너 다섯 개를 20초 등록하고 콘솔을 80·20·60으로 바꿔도 처음 0 한 번만 왔다. Pleos 서비스가 보내는 `SEAT_FORE_AFT_POS` 등도 모든 area에서 0이라, 콘솔 시트 값이 차량 속성까지 가지 않는 것으로 보인다(에뮬레이터 쪽). 시트 코드는 시간 방식 그대로 둔다(보류) |
| 시트 | CarPropertyManager SEAT_FORE_AFT_POS·SEAT_HEIGHT_POS·SEAT_BACKREST_ANGLE_1_POS·SEAT_TILT_POS·SEAT_DEPTH_POS·SEAT_LUMBAR_FORE_AFT_POS·SEAT_LUMBAR_SIDE_SUPPORT_POS·SEAT_HEADREST_HEIGHT_POS_V2 (운전석) | 직접(데모 `seatread`) | 실패 | 2026-11-09 측정. 콘솔 0일 때와 100일 때 모두 `SecurityException: requires android.car.permission.CONTROL_CAR_SEATS`. 이 권한은 `signature\|privileged`라 debug 매니페스트에 선언해도 `granted=false`. SEAT_BACKREST_ANGLE_2_POS·SEAT_CUSHION_SIDE_SUPPORT_POS는 에뮬레이터에 속성이 없다. `cmd car_service get-property-value`도 user 빌드라 막힌다 |
| 시트 | moveSeat{…}(area, FORWARD/BACKWARD/STOP) | 직접 | 반영 | 2026-11-09 probe 측정. 앞뒤·높이·등받이·쿠션 각도 모두 FORWARD로 콘솔 값이 0→100 증가. 읽기가 0에 고정돼 목표에서 못 멈추고 10초 제한 후 STOP. 되돌리기도 "이미 0"으로 판단해 움직이지 않아 콘솔 API로 0에 복구했다. 쿠션 길이는 콘솔에 항목 없음 |
| 시트 | selectSeatMemory(area, n) | 직접 | | 2026-11-09 probe 측정. 1번 호출, 실패 콜백 없이 1초 후 Sent. 콘솔 앞뒤 위치 변화 없음(100→100). 지원은 운전석 1~2번, 쓰기만(`checkSeatMemoryCapabilities`). SDK 2.3.2에 메모리 저장 API(save·set·store 계열)가 없고 콘솔 카탈로그에도 메모리 항목이 없어 "저장 → 움직임 → 불러오기" 확인은 못 했다 |
| 공통 | getGear().registerGearSelection | 직접 | 반영 | 2026-11-09 측정. P→D→R→P 모두 전달(`VENDOR_GEAR_SELECTION` 4/8/2/4), 앱 상태 PARK/DRIVE/PARK 확인. R에서는 후방 카메라가 앱을 가려 화면 확인은 못 함. deprecated, GEAR_SELECTION 속성 권장 – 실측 때 비교 |
| 공통 | getCurrentDoorPosition / registerDoorPosition | 직접 | 반영 | 2026-11-09 측정. ROW_2_LEFT/RIGHT 열기·닫기 모두 전달(`DOOR_POS` area 16/64, 1/0), 앱 상태 줄 일치 |
| 주유 | Fuel.getFuelPercent / registerFuelPercent | 직접 | 실패 | 2026-11-09 측정. `property ID 0x21400be5 is not in carPropertyConfig list`. 에뮬레이터 차는 전기차라 연료 % 속성이 없다. 데모 `fuel N`으로 덮어써 시연한다 |
| 주유 | Fuel.getCurrentFuelEconomy | 직접 | 실패 | 2026-11-09 측정. `ERROR_NOT_SUPPORTED_PROPERTY` |
| 주유 | Odometer.getRangeRemainingDistance / registerRangeRemainingListener | 직접 | 반영 | 2026-11-09 측정. 콘솔 `odometer.remaining_range_m` 350000→120000→0에 앱 350000→120000→0. 단위 m |
| 주유 | EvBattery.getEvBatteryStateOfCharge / registerEvBatteryStateOfCharge | 직접 | 반영 | 2026-11-09 측정. 콘솔 SoC 40→15→100에 앱 40→15→100 |
| 주유 | AAOS RANGE_REMAINING·EV_BATTERY_LEVEL·INFO_EV_BATTERY_CAPACITY | 직접 | 반영 | 2026-11-09 측정. `android.car.permission.CAR_ENERGY`(dangerous)를 MainActivity에서 요청해 허가받음. 주행 가능 거리 m, 배터리 Wh(콘솔 30kWh → 30000). 같은 값도 1초마다 다시 온다 |
| 주유 | AAOS FUEL_LEVEL·INFO_FUEL_CAPACITY | 직접 | 읽기값만 | 2026-11-09 측정. 둘 다 15000ml로 고정. 콘솔에 연료 칸이 없어 바꿀 수 없다. 에뮬레이터 기본값으로 보인다(미확인) |
| 공통 | PERF_VEHICLE_SPEED (AAOS) | 직접 | 반영 | 2026-11-09 측정. 콘솔 카탈로그에는 속도 항목이 없지만 `controls.speed_kph`가 있고 `PUT /vehicle/controls?speed=N`으로 바뀐다. 30·55·7·0km/h 모두 앱 상태 줄에 같은 값(`AaosSpeedSource`, 2초 안). 데모 `speed`는 이제 콘솔이 없을 때만 필요 |
| 공통 | 위치(위도·경도) | | 실패 | 2026-11-09 측정. 콘솔 카탈로그 63개 항목에 위치·GPS 항목이 없다. 장소는 데모 `place`로 넣는다 |
| 주유 | AAOS INFO_FUEL_TYPE (차 종류) | 직접 | 반영 | 2026-11-09 측정. `[10]`(ELECTRIC) → 상태 줄 "전기차". `CAR_INFO`(설치 때 허가). SDK `CarInfo`에는 VIN·CRN·지원 목록만 있고 연료 종류가 없다 |

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

## 시트 축 이동 시간 (2026-11-09, 데모 `seattime`)

시트 위치를 앱이 읽을 수 없어서(SDK는 늘 0, AAOS 속성은 권한 없음) 움직인 시간으로 위치를 맞출 수 있는지 잰다.
운전석, SDK `moveSeat…`로 FORWARD/BACKWARD를 보내고 콘솔 `seat.positions.<축>.driver`가 100(또는 0)에 닿을 때까지 0.1초마다 읽었다. 축·방향마다 5번. 원본: `relay/files/20261109-1855-시트이동시간.csv`.

| 축 (콘솔 이름) | 0→100 평균 (최소~최대) | 100→0 평균 (최소~최대) |
|---|---|---|
| 앞뒤 (slide) | 2.08초 (2.06~2.09) | 2.09초 (2.06~2.13) |
| 높이 (height) | 2.05초 (2.04~2.06) | 2.04초 (2.04~2.05) |
| 등받이 (recline) | 2.05초 (2.04~2.05) | 2.05초 (2.04~2.07) |
| 쿠션 각도 (tilt) | 2.05초 (2.03~2.13) | 2.06초 (2.04~2.14) |

- 네 축 모두 끝에서 끝까지 약 2.05초, 방향 차이 없음. 1단위(콘솔 %)에 약 20밀리초. 읽기 간격 때문에 ±0.1초 오차가 있다.
- 쿠션 길이·머리받이는 콘솔에 칸이 없어 재지 못했다.
- 에뮬레이터 값이라 실제 차 속도와 다를 수 있다(미확인). 아직 코드에 넣지 않았다.

## 시트 시간 맞추기 (2026-11-09, car/seat-timing, 데모 `seatfit`)

`PleosCarPort.moveSeatTo()`: BACKWARD 2.5초 → STOP(추정 0) → FORWARD (목표 × 축별 0→100 시간 / 100) → STOP. 추정 위치를 앱이 기억하고 시트 위치 읽기는 이 값을 돌려준다. 결과는 Sent.
운전석, 목표 30·70을 축마다 3번씩, 움직인 뒤 콘솔 `seat.positions.<축>.driver`와 비교. 원본: `relay/files/20261109-2006-시트시간맞추기.csv`.

| 축 | 목표 30 (콘솔, 3번) | 목표 70 (콘솔, 3번) | 오차 |
|---|---|---|---|
| 앞뒤 (slide) | 30, 30, 30 | 70, 70, 70 | 0 |
| 높이 (height) | 30, 30, 30 | 70, 70, 70 | 0 |
| 등받이 (recline) | 30, 30, 30 | 70, 70, 70 | 0 |
| 쿠션 각도 (tilt) | 30, 30, 30 | 70, 70, 70 | 0 |

- 24번 모두 콘솔 정수 단위로 오차 0. 콘솔이 정수(1단위 약 20밀리초)로만 보여서 그보다 작은 오차는 보이지 않는다.
- 기어 변경 중단: 앞뒤를 콘솔 100에 두고 이동 시작 뒤 기어를 D로 바꿈. 앱이 기어 D를 받은 지 32밀리초 뒤 STOP(logcat 03:04:58.723 → 58.755), 그때 콘솔 40. 결과 Failed("기어가 바뀌어서 시트를 멈췄어요"), 이어진 요청은 "기어가 P가 아니라서 시트를 움직이지 않았어요"로 움직이지 않음.
- 한계: 사람이 시트를 손으로(또는 콘솔로) 움직이거나 앱을 다시 켜면 추정 위치가 실제와 달라진다. 다음 이동은 늘 맨 뒤로 기준점을 다시 잡아서 이동 자체는 맞지만, 되돌리기용 "원래 값"은 틀릴 수 있다.
- 실차 속도는 미확인. 실차에서 축별 0→100 시간을 다시 재야 한다.

