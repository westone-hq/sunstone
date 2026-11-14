# Pleos API 실측 기록

원본 기록(CSV, logcat, 캡처)은 저장소에 올리지 않았다. 본문에는 파일 이름만 남긴다.

에뮬레이터: Pleos Connect v2.1.0 / Vehicle SDK 2.3.2. Pleos 공식 Vehicle 샘플 앱(저장소에는 올리지 않음)으로 하나씩 눌러보고 Virtual Console 값과 비교한다.
제어는 debug 빌드 데모 명령 `probe`로 대상마다 "원래 값 읽기 → 바꾸기 → 1초 → 다시 읽기 → 되돌리기 → 1초 → 다시 읽기"를 하고, 같은 순간의 콘솔 값을 함께 남긴다(원본 CSV: 처음 `20261109-1656-제어실측.csv`, area 수정 후 `20261109-1840-제어실측-수정후.csv`).
앱이 다시 읽은 값과 콘솔 값이 다른 행은 메모에 **불일치**로 표시한다.
결과: `반영` · `읽기값만` (다시 읽은 값만 바뀜·지연) · `실패` (실패·API 없음)
시연 표기: 시연에서 그 값을 어떻게 보여 주는지.
- `실제` — 앱이 SDK·AAOS에서 읽거나 실제로 제어한다. "콘솔로 장면 준비"는 에뮬레이터 차 값을 Virtual Console로 맞춰 둔다는 뜻이고, 앱은 그 값을 차에서 읽는다
- `조작(무엇으로)` — 프로토타입이라 앱이 차 값 대신 데모 명령·추정값·고정 규칙을 쓴다. 코드 주석 `미구현: 프로토타입이라 …`와 짝이다
- `쓰지 않음` — 실측만 하고 기능에는 쓰지 않는다

Virtual Console은 브라우저 세션이 열려 있을 때만 차량 쪽과 연결된다. 페이지를 열어 두거나
`POST /browser/session?client=<이름>`을 1초마다 보낸다(3초 끊기면 `emulator_inactive`).
읽기 판정 근거: Pleos 서비스 로그 `broadcastPropertyCallback(applicationId = com.westone.sunstone …)`와 앱 상태 줄.

| 루틴 | API | 우리 코드 호출 방식 | 결과 | 시연 표기 | 메모 |
|---|---|---|---|---|---|
| 세차 | getCurrentWindowPosition / setWindowPosition (ROW_1_LEFT…ROW_2_RIGHT) | 직접 | 반영 | 실제 | 2026-11-09 probe 측정. 4개 모두 0→5→0, 앱 다시 읽기 5/0, 콘솔 50/0(%, 앱 값 ×10). 바꾸기·되돌리기 각 1.5초(확인 대기 포함). 다른 팀 사례(0으로 되돌리기 실패)는 재현 안 됨 |
| 세차 | setWindowPosition ROOF_TOP_1/2 (선루프) | 직접 | 실패 | 실제(지원 안 함 → 안내) | 2026-11-09 probe 측정. 읽기·쓰기 모두 `area ID: 0x10000/0x20000 not supported for property ID: WINDOW_POS`. 에뮬레이터에 선루프 없음. 콘솔에도 항목 없음. 2026-11-09 car/seat-timing: `checkWindowPositionCapabilities`를 먼저 보고 Unsupported로 안내, 실행 기록에 "이 차에서는 지원하지 않아요" |
| 세차 | getCurrentWiperSwitch / setWiperSwitch (OFF·AUTO) | 직접 | 반영 | 실제 | 2026-11-09 probe 측정. OFF→AUTO→OFF, 앱 AUTO/OFF, 콘솔 Auto/Off. 0.4초. 리플렉션 `setWiperSwitch` 존재 확인 |
| 세차 | getHvacRecirculationState / setHvacRecirculation | 직접 | 반영 | 실제 | 2026-11-09 수정 후 probe. 다섯 좌석 합친 area(117)로 부르니 false→true→false, 앱·콘솔(`hvac.recirc_on`) 일치, 0.4초. 처음엔 ROW_1_LEFT(0x1)로 불러 `not supported` 실패 |
| 세차·차일드락 | getDoorLockState / setDoorLock (네 문 합친 area 85) | 직접 | 반영 | 실제 | 2026-11-09 수정 후 probe. 문 전체 잠금 하나로 다룬다. true→false→true, 앱·콘솔(`body.door_locks.all`) 일치, 0.4초. 처음엔 문마다 불러 `area ID: 0x1 not supported for property ID: DOOR_LOCK` 실패 |
| 차일드락 | getCurrentWindowLockState / setWindowLock (ROW_2_*) | 직접 | 읽기값만 | 실제(SDK 다시 읽기, 콘솔은 불일치) | 2026-11-09 probe 측정(수정 후 같음). **불일치**: 뒷좌석 2개 false→true→false, 앱 다시 읽기 true/false, 콘솔은 계속 false. 다른 팀 사례(읽기값만 true, 콘솔 false)와 같다. 2026-11-09 car/upgrade: SDK 다시 읽기는 반영되고 콘솔만 불일치(에뮬레이터 쪽으로 보임)라 다시 읽은 값으로 판단해 잠김이면 Done. 차일드락 루틴에서 뒷창 좌우 Done 확인. 실차 미확인. 앞좌석은 따로 잠글 area가 없어(네 창문 합친 area만) 보내기 전에 Unsupported |
| 차일드락 | moveWindowBlind (REAR_WINDSHIELD) / getCurrentWindowBlindPosition | 직접 | 실패 | 실제(지원 안 함 → 안내) | 2026-11-09 probe 측정. `area ID: 0x2 not supported for property ID: 0x23400532`. 지원 area는 옆 창문 네 곳(ROW_1/2 LEFT/RIGHT)뿐이고 뒷유리는 없다. 수정 후에는 지원 목록을 먼저 보고 Unsupported로 안내. 콘솔에도 항목 없음 |
| 차일드락 | getHvacPowerState / setHvacPower (다섯 좌석 합친 area 117) | 직접 | 반영 | 조작(좌석별 요청을 차 전체 공조로) | 2026-11-09 수정 후 probe. 좌석별 전원은 없고 차 전체 전원 하나다. 어느 좌석 요청이든 전체 전원으로 부르면 false→true→false, 앱·콘솔(`hvac.power_on`) 일치, 0.4초. 처음엔 좌석마다 불러 `not supported` 실패. "뒷좌석 공조 켜기"가 차 전체 공조를 켠다 |
| 차일드락 | getCurrentSeatOccupancy / registerSeatOccupancy | 직접 | 실패 | 조작(에뮬레이터에서만 값 반전) | 2026-11-09 측정. 값은 오지만 반대로 읽힌다. 에뮬레이터는 AAOS 기준 1=빈자리, 2=탑승(`SEAT_OCCUPANCY` area 1/4/16/64)을 보내는데 SDK 2.3.2는 `값 == 1`을 탑승(true)으로 바꾼다. 운전석·조수석·뒷좌석 좌우 4곳 모두 콘솔 탑승→앱 빈자리, 콘솔 빈자리→앱 탑승. 뒷좌석 가운데는 콘솔에 없고 앱은 늘 탑승. 2026-11-09 `PleosCarPort.INVERT_OCCUPANCY`로 뒤집고 가운데는 읽지 않게 바꾼 뒤, 콘솔 4자리 탑승/빈자리가 앱 상태 줄과 일치. car/upgrade부터 `Build.HARDWARE`가 에뮬레이터(`ranchu`)일 때만 뒤집는다. 다른 팀도 에뮬레이터에서 같은 현상. 실차 미확인 |
| 차일드락 | getCurrentSeatBeltBuckledState / registerSeatBeltBuckledState (ROW_2_*) | 직접 | 읽기값만 | 조작(데모 `belt 좌석 on\|off`) | 2026-11-10 데모 `belt`. 운전석·뒷좌석 좌우 모두 읽기·리스너가 늘 false. 콘솔에 좌석별 벨트 칸이 없고, 차 경고등 `SeatBelt`를 켜거나 뒷좌석 탑승을 바꿔도 그대로 false. 원본 `20261110-1442-벨트실측.csv` 2026-11-11 navi/live: 데모 `belt`로 좌석별 벨트를 덮어쓴다(`PleosCarPort.overrideBelt`). 차일드락을 켜고 콘솔로 D·30km/h에서 `belt rear_left off`를 넣자 같은 순간 "뒷좌석 왼쪽 안전벨트가 풀렸어요." 안내(`20261111-1210-벨트풀림-주행중.png`) |
| 차일드락 | Safety.hasSeatBeltNotBuckledWarning / registerSeatBeltNotBuckledWarning / checkSeatNotBuckledWarningCapabilities | 직접(데모 `belt`) | 실패 | 쓰지 않음 | 2026-11-10 측정. `pleos.car.permission.CAR_SAFETY`(normal)를 넣은 뒤에도 `ERROR_NOT_SUPPORTED_PROPERTY`, 속성 0x25200709가 에뮬레이터에 없다 |
| 차일드락 | Safety.getTelltalesInfo / registerTelltales (경고등 SEAT_BELT) | 직접(데모 `belt`) | 반영 | 쓰지 않음 | 2026-11-10 측정. 콘솔 `PUT /vehicle/telltales/simulator?type=SeatBelt&state=On\|Blink\|Off&color=Red`(차량 속성 `TELLTALES` 0x21410900, area 0)가 1초 안에 리스너로 왔다: ON/4 → 없음 → BLINK/4. 차 전체 경고등 하나라 좌석을 모른다. `pleos.car.permission.PRIVATE_CAR_SAFETY`가 있어야 읽혀서(CAR_SAFETY만으로는 `need Permission`) debug 매니페스트에만 넣었다. 실차에서 이 권한을 받을 수 있는지 미확인 2026-11-11: 좌석을 모르고 PRIVATE 권한이라 쓰지 않기로 하고 `PRIVATE_CAR_SAFETY`를 debug 매니페스트에서도 뺐다. 실측 명령 이름은 `beltprobe`로 바꿨다 |
| 차일드락 | Pleos SDK Door 차일드락 | 없음 | 실패 | 쓰지 않음(안내 문장) | 2026-11-11 확인. SDK 2.3.2 `Door` 공개 함수는 문 잠금·문 위치·글러브박스뿐이고 차일드락이 없다(javap) |
| 차일드락 | AAOS DOOR_CHILD_LOCK_ENABLED (0x16200b03, 뒷문 area 0x10·0x40) | 직접(데모 `childlock`) | 실패 | 쓰지 않음(안내 문장) | 2026-11-11 측정. 에뮬레이터 차 서비스에 속성은 있다(`dumpsys car_service --hal`: access 0x3 읽기·쓰기). 그러나 읽기·쓰기 모두 `SecurityException: requires android.car.permission.CONTROL_CAR_DOORS`. 이 권한은 `signature\|privileged`라 debug 매니페스트에 선언해도 `granted=false`. 콘솔 카탈로그에도 차일드락 항목이 없다. 코드에 넣지 않고, 시나리오 ②는 "뒷문 차일드락은 문 안쪽 스위치로 걸어 주세요." 안내로 간다. 원본 `20261111-1151-문차일드락.csv` |
| 시트 | getCurrentSeat{ForeAft,Height,BackrestAngle,Tilt,Depth,HeadrestHeight}Position | 직접 | 실패 | 조작(시간 기반 추정값) | 2026-11-09 probe 측정. **불일치**: 앞뒤·높이·등받이·쿠션 각도·쿠션 길이는 함수가 있으나 늘 0을 돌려준다(콘솔 100일 때도 0). 머리받이 읽기 함수는 SDK 이름이 `getCurrentSeatHeadrestHeightAftPosition`이라 리플렉션이 못 찾았다. 직접 호출로 바꾼 뒤에도 늘 0. 2026-11-09 데모 `seatcheck`: `check…PositionCapabilities`는 앞뒤·높이·등받이·쿠션 각도 READ_WRITE(1/4/16/64, 0~100), 머리받이 READ_WRITE(앞좌석, -10~10)로 지원한다고 나온다. 그러나 지원 목록이 준 area 그대로 읽어도 0(콘솔 40), 위치 리스너 다섯 개를 20초 등록하고 콘솔을 80·20·60으로 바꿔도 처음 0 한 번만 왔다. Pleos 서비스가 보내는 `SEAT_FORE_AFT_POS` 등도 모든 area에서 0이라, 콘솔 시트 값이 차량 속성까지 가지 않는 것으로 보인다(에뮬레이터 쪽). 시트 코드는 시간 방식 그대로 둔다(보류) |
| 시트 | CarPropertyManager SEAT_FORE_AFT_POS·SEAT_HEIGHT_POS·SEAT_BACKREST_ANGLE_1_POS·SEAT_TILT_POS·SEAT_DEPTH_POS·SEAT_LUMBAR_FORE_AFT_POS·SEAT_LUMBAR_SIDE_SUPPORT_POS·SEAT_HEADREST_HEIGHT_POS_V2 (운전석) | 직접(데모 `seatread`) | 실패 | 쓰지 않음 | 2026-11-09 측정. 콘솔 0일 때와 100일 때 모두 `SecurityException: requires android.car.permission.CONTROL_CAR_SEATS`. 이 권한은 `signature\|privileged`라 debug 매니페스트에 선언해도 `granted=false`. SEAT_BACKREST_ANGLE_2_POS·SEAT_CUSHION_SIDE_SUPPORT_POS는 에뮬레이터에 속성이 없다. `cmd car_service get-property-value`도 user 빌드라 막힌다 |
| 시트 | moveSeat{…}(area, FORWARD/BACKWARD/STOP) | 직접 | 반영 | 실제(시간만큼 보냄) | 2026-11-09 probe 측정. 앞뒤·높이·등받이·쿠션 각도 모두 FORWARD로 콘솔 값이 0→100 증가. 읽기가 0에 고정돼 목표에서 못 멈추고 10초 제한 후 STOP. 되돌리기도 "이미 0"으로 판단해 움직이지 않아 콘솔 API로 0에 복구했다. 쿠션 길이는 콘솔에 항목 없음 |
| 시트 | 조수석 moveSeat{ForeAft,Height,BackrestAngle,Tilt}(ROW_1_RIGHT) | 직접(데모 `seattime passenger`·`seatfit passenger`) | 반영 | 실제(시간만큼 보냄) | 2026-11-11 측정. 네 축 모두 콘솔 `seat.positions.<축>.passenger`가 0→100·100→0으로 움직인다. 끝에서 끝 2.03~2.14초(아래 이동 시간 표). `PleosCarPort.SEAT_FULL_MS`를 좌석·축별로 넓혀 조수석 값을 넣었고, 목표 30·70 × 3번 × 4축 24번 모두 콘솔 오차 0. 원본 `20261111-1153-조수석시트이동시간.csv`, `20261111-1155-조수석시트맞추기.csv` |
| 시트 | selectSeatMemory(area, n) | 직접 |  | 실제(보냄, 확인 못 함) | 2026-11-09 probe 측정. 1번 호출, 실패 콜백 없이 1초 후 Sent. 콘솔 앞뒤 위치 변화 없음(100→100). 지원은 운전석 1~2번, 쓰기만(`checkSeatMemoryCapabilities`). SDK 2.3.2에 메모리 저장 API(save·set·store 계열)가 없고 콘솔 카탈로그에도 메모리 항목이 없어 "저장 → 움직임 → 불러오기" 확인은 못 했다 |
| 공통 | getGear().registerGearSelection | 직접 | 반영 | 실제(콘솔로 장면 준비) | 2026-11-09 측정. P→D→R→P 모두 전달(`VENDOR_GEAR_SELECTION` 4/8/2/4), 앱 상태 PARK/DRIVE/PARK 확인. R에서는 후방 카메라가 앱을 가려 화면 확인은 못 함. deprecated, GEAR_SELECTION 속성 권장 – 실측 때 비교 |
| 공통 | getCurrentDoorPosition / registerDoorPosition | 직접 | 반영 | 실제(콘솔로 장면 준비) | 2026-11-09 측정. ROW_2_LEFT/RIGHT 열기·닫기 모두 전달(`DOOR_POS` area 16/64, 1/0), 앱 상태 줄 일치 |
| 주유 | Fuel.getFuelPercent / registerFuelPercent | 직접 | 실패 | 조작(데모 `fuel N`) | 2026-11-09 측정. `property ID 0x21400be5 is not in carPropertyConfig list`. 에뮬레이터 차는 전기차라 연료 % 속성이 없다. 데모 `fuel N`으로 덮어써 시연한다 |
| 주유 | Fuel.getCurrentFuelEconomy | 직접 | 실패 | 쓰지 않음 | 2026-11-09 측정. `ERROR_NOT_SUPPORTED_PROPERTY` |
| 주유 | Odometer.getRangeRemainingDistance / registerRangeRemainingListener | 직접 | 반영 | 실제(콘솔로 장면 준비) | 2026-11-09 측정. 콘솔 `odometer.remaining_range_m` 350000→120000→0에 앱 350000→120000→0. 단위 m |
| 주유 | EvBattery.getEvBatteryStateOfCharge / registerEvBatteryStateOfCharge | 직접 | 반영 | 실제(콘솔로 장면 준비) | 2026-11-09 측정. 콘솔 SoC 40→15→100에 앱 40→15→100 |
| 주유 | AAOS RANGE_REMAINING·EV_BATTERY_LEVEL·INFO_EV_BATTERY_CAPACITY | 직접 | 반영 | 실제(콘솔로 장면 준비) | 2026-11-09 측정. `android.car.permission.CAR_ENERGY`(dangerous)를 MainActivity에서 요청해 허가받음. 주행 가능 거리 m, 배터리 Wh(콘솔 30kWh → 30000). 같은 값도 1초마다 다시 온다 |
| 주유 | AAOS FUEL_LEVEL·INFO_FUEL_CAPACITY | 직접 | 읽기값만 | 쓰지 않음(고정값) | 2026-11-09 측정. 둘 다 15000ml로 고정. 콘솔에 연료 칸이 없어 바꿀 수 없다. 에뮬레이터 기본값으로 보인다(미확인) |
| 공통 | PERF_VEHICLE_SPEED (AAOS) | 직접 | 반영 | 실제(콘솔 `controls.speed`) / 콘솔 없으면 조작(데모 `speed`) | 2026-11-09 측정. 콘솔 카탈로그에는 속도 항목이 없지만 `controls.speed_kph`가 있고 `PUT /vehicle/controls?speed=N`으로 바뀐다. 30·55·7·0km/h 모두 앱 상태 줄에 같은 값(`AaosSpeedSource`, 2초 안). 데모 `speed`는 이제 콘솔이 없을 때만 필요 |
| 공통 | 위치(위도·경도) |  | 반영 | 실제(`tools/scene-move.ps1`로 장면 준비), 예비로 데모 `place` | 2026-11-09 측정. 콘솔 카탈로그 63개 항목에 위치·GPS 항목이 없다. 장소는 데모 `place`로 넣는다. 2026-11-10: 아래 FusedLocation 행처럼 `adb emu geo fix`로 바꿀 수 있어 실제로 바꿀 수 있다(앱 연결은 아직) 2026-11-11 navi/live: 시연 장소(`assets/places.json`)마다 근접 알림을 걸어(`PlaceWatcher`) 들어가면 그 장소, 나가면 장소 없음으로 바꾼다. 장소 목록은 바깥 API 대신 고정 목록(주유소·유치원·집) |
| 공통 | FusedLocation 1.1.3 registerFusedLocationCallback / getLastKnownLocation | 직접(데모 `location`) | 반영 | 실제(`adb emu geo fix`로 장면 준비) | 2026-11-10 측정. `geo fix` 서울 → 해운대 → 서울 → 해운대 350m 밖 → 100m 안을 1초 안에 모두 따라왔다(1초마다 콜백, confidence 5.0). `pleos.car.permission.FUSED_LOCATION`(dangerous)을 MainActivity에서 요청한다. 권한 창 제목이 "알 수 없는 작업"으로 나온다. 원본 `20261110-1437-위치실측.csv` |
| 세차·주유 | FusedLocation addProximityAlert / removeProximityAlert (ENTER·EXIT) | 직접(데모 `location`) | 반영 | 실제(`tools/scene-move.ps1`로 장면 준비) | 2026-11-10 측정. 해운대해수욕장 반경 200m: 들어가면 ENTER, 서울로 옮기면 EXIT, 350m 밖은 알림 없음, 100m 안은 ENTER. 위치가 바뀐 그 콜백과 같은 순간에 왔다. 세차장 도착 감지를 데모 `place` 대신 이것으로 할 수 있다 2026-11-11 navi/live: 시나리오 ① 스크립트로 서울 → 주유소를 1초마다 옮기니 반경 200m에 들어간 순간 장소 = 주유소, 정차·P가 된 1초 뒤 "세차하시나요? 준비해 드릴까요?"(데모 명령 없음). 시나리오 ②는 집 → 유치원 → 집 순서로 들어감·나감이 왔다 |
| 길안내 | NaviHelper 3.0.5 requestRoute(RouteInfo 좌표) | 직접(데모 `navi`) | 반영 | 실제 | 2026-11-10 측정. 해운대해수욕장 좌표로 요청하면 지도 앱이 앞으로 나와 경로를 그린다(382km, 4시간 57분). `onRouteStarted(duration 17820초, distance 382000m, tollFare 16010원)`. `NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`·`NAVI_NOTIFY`(모두 normal)가 없으면 `SecurityException`. `RouteInfo`는 poiId·address·poiSubId를 꼭 넘겨야 해서 빈 글자로 넘긴다 |
| 길안내 | addWaypoint(FIRST·SECOND) | 직접(데모 `navi`) | 반영 | 실제 | 2026-11-10 측정. 주유소 지점(서울 양재 부근)·대전을 더하면 그때마다 경로를 다시 찾는다(`onRouteCancelled` → `onWaypointChanged(ADD, 그 경유지까지 RouteStartInfo)` → `onRouteEnded`·`ROUTE_DRIVE`). 화면에 "경유지 2개 포함, 429km, 6시간 14분, 19,120원". `WaypointIndex`가 FIRST·SECOND·ALL뿐이라 이 함수로는 2개까지 |
| 길안내 | requestRouteWithWaypoints(경유지 3개) | 직접(데모 `navi`) | 반영 | 쓰지 않음 | 2026-11-10 측정. 2개 제한을 확인하려고 3개(주유소·대전·대구)를 넣었더니 그대로 받았다. `onWayPointInfo` 3개, `onDrivingInfo` 경유지 3개, 화면 진행 막대에도 3곳(13·163·312km). 2개 제한은 `addWaypoint` 쪽만이다. 실차 동작 미확인이라 기능은 2개까지만 쓴다 |
| 길안내 | getDestinationInfo / getWaypointInfo / onDrivingInfo (남은 거리·시간) | 직접(데모 `navi`) | 반영 | 실제 | 2026-11-10 측정. 목적지 남은 거리 429000m·시간 22440초(경유지 2개 포함), 경유지마다 남은 거리·시간도 온다. 위치·좌표는 경유지에서 0.0으로 온다(목적지는 좌표가 온다). 차가 움직이지 않아(아래 내비 현재 위치) 남은 거리가 줄어드는지는 못 봤다 |
| 길안내 | 통행료 (RouteStartInfo.tollFare / DestinationInfo.tollFare) | 직접(데모 `navi`) | 읽기값만 | 실제(경로 시작·경유지 변경 때 값) | 2026-11-10 측정. `onRouteStarted`·`onWaypointChanged`의 `tollFare`는 16010·16710·19120원으로 화면과 같다. `getDestinationInfo().tollFare`는 늘 0. 통행료는 경로 시작 이벤트에서 읽는다 |
| 길안내 | onDestinationArrived / onWayPointArrived | 직접(데모 `navi`) | 실패 | 실제(근접 알림으로 대신) | 2026-11-10 측정. 리스너를 60초 등록하고 `geo fix`로 해운대에 옮겼지만 오지 않았다. 내비 현재 위치가 움직이지 않아서다(아래 "내비 현재 위치"). 에뮬레이터에서는 도착을 만들 수 없다 2026-11-11 navi/live: `PleosNavi`가 경로 시작·경유지 추가 때 그 좌표에 근접 알림(200m)을 걸어 도착을 만든다. 내비 도착 이벤트가 오면 같이 흘리고 60초 안 같은 곳은 한 번만. 경유지 주유소로 geo fix를 옮기자 "도착,OO주유소(세차 가능),경유지" |
| 길안내 | cancelRoute | 직접(데모 `navi`) | 반영 | 실제 | 2026-11-10 측정. `onRouteCancelled`, `FREE_DRIVE`, 목적지·경유지 비워짐 |
| 길안내 | 장소 검색(이름·분류) | 없음 | 실패 | 쓰지 않음 | 2026-11-10 확인. NaviHelper 3.0.5 공개 함수(javap)에 이름·분류 검색이 없다(경로·경유지·즐겨찾기·최근 목적지·현재 위치·TBT·충전 사업자·화면·Trip뿐). 지도 앱이 Gleo용 `ai.umos.maps.android.navigation.app.SEARCH_PLACE` 액션을 받지만 Gleo 전용 통로라 시험하지 않았다. 장소 검색은 바깥 API(카카오 로컬)로 한다 |
| 주유 | AAOS INFO_FUEL_TYPE (차 종류) | 직접 | 반영 | 실제 | 2026-11-09 측정. `[10]`(ELECTRIC) → 상태 줄 "전기차". `CAR_INFO`(설치 때 허가). SDK `CarInfo`에는 VIN·CRN·지원 목록만 있고 연료 종류가 없다 |

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
운전석, SDK `moveSeat…`로 FORWARD/BACKWARD를 보내고 콘솔 `seat.positions.<축>.driver`가 100(또는 0)에 닿을 때까지 0.1초마다 읽었다. 축·방향마다 5번. 원본: `20261109-1855-시트이동시간.csv`.

| 축 (콘솔 이름) | 운전석 0→100 평균 (최소~최대) | 운전석 100→0 | 조수석 0→100 | 조수석 100→0 |
|---|---|---|---|---|
| 앞뒤 (slide) | 2.08초 (2.06~2.09) | 2.09초 (2.06~2.13) | 2.04초 (2.03~2.05) | 2.03초 (2.03~2.04) |
| 높이 (height) | 2.05초 (2.04~2.06) | 2.04초 (2.04~2.05) | 2.14초 (2.13~2.14) | 2.10초 (2.03~2.14) |
| 등받이 (recline) | 2.05초 (2.04~2.05) | 2.05초 (2.04~2.07) | 2.07초 (2.03~2.14) | 2.11초 (2.03~2.14) |
| 쿠션 각도 (tilt) | 2.05초 (2.03~2.13) | 2.06초 (2.04~2.14) | 2.10초 (2.03~2.14) | 2.07초 (2.03~2.13) |

- 네 축 모두 끝에서 끝까지 약 2.05초, 방향 차이 없음. 1단위(콘솔 %)에 약 20밀리초. 읽기 간격 때문에 ±0.1초 오차가 있다.
- 쿠션 길이·머리받이는 콘솔에 칸이 없어 재지 못했다.
- 에뮬레이터 값이라 실제 차 속도와 다를 수 있다(미확인). 0→100 평균을 `PleosCarPort.SEAT_FULL_MS`에 좌석·축별로 넣었다.
- 조수석은 2026-11-11 데모 `seattime passenger`로 쟀다. 같은 날 운전석을 다시 재니 2.05~2.09초로 이전과 같았다(`20261111-1151-운전석시트재확인-logcat.txt`).

## 시트 시간 맞추기 (2026-11-09, car/seat-timing, 데모 `seatfit`)

`PleosCarPort.moveSeatTo()`: BACKWARD 2.5초 → STOP(추정 0) → FORWARD (목표 × 축별 0→100 시간 / 100) → STOP. 추정 위치를 앱이 기억하고 시트 위치 읽기는 이 값을 돌려준다. 결과는 Sent.
운전석, 목표 30·70을 축마다 3번씩, 움직인 뒤 콘솔 `seat.positions.<축>.driver`와 비교. 원본: `20261109-2006-시트시간맞추기.csv`.

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

## 내비 실측 (2026-11-10, 데모 `navi`)

NaviHelper 3.0.5(`ai.pleos.playground:NaviHelper:3.0.5`, navi-data 3.0.4 함께 받음). 에뮬레이터 내비 서비스 `ai.pleos.playground.service.navi`(서비스 3.0.4), 지도 앱 `ai.umos.maps.android.navigation.app` 3.3.2.
단계마다 10초 기다리며 화면을 캡처했다. 원본: `20261110-1428-내비실측.csv`, 캡처 `20261110-1428-navi-*.png`.

| 단계 | 화면 | 받은 값 |
|---|---|---|
| 현재 위치·경로 상태 | 지도, 현재 위치 수정구 시흥동 | `CurrentLocationInfo(37.4127, 127.0947)`, `FREE_DRIVE` |
| 목적지 해운대해수욕장 | 경로·안내 시작(382km, 4시간 57분) | `onRouteStarted` 17820초·382000m·16010원 |
| 경유지 1 시험 주유소 | 다시 찾은 경로 | `onWaypointChanged(FIRST, ADD)` 2280초·13000m·16710원 |
| 경유지 2 대전 | 경로 고르기 "경유지 2개 포함" 429km·6시간 14분·19,120원 | `onWaypointChanged(FIRST, ADD)` (index가 SECOND가 아니라 FIRST로 옴) |
| 남은 거리·시간·통행료 | 안내 중 | 목적지 429000m·22440초·통행료 0, 경유지 2개 각각 남은 거리·시간 |
| 경유지 3개 요청 | 경로 다시 시작, 진행 막대 3곳 | `onWayPointInfo` 3개 |
| 도착 대기 60초 + `geo fix` 해운대 | 현재 위치 그대로(수정구 시흥동) | 도착 이벤트 없음 |
| 안내 취소 | 안내 끝 | `onRouteCancelled`, `FREE_DRIVE` |

- **앱 얼림:** `requestRoute`를 부르면 지도 앱이 앞으로 나오고 우리 앱은 뒤로 간다. 안드로이드 14의 앱 얼림(cached apps freezer)에 걸려(`isFrozen=true`) 코루틴이 멈추고 내비 이벤트도 늦게 온다. 첫 시도에서 `onRouteStarted`가 6분 44초 뒤 앱을 깨운 순간에야 왔다(`20261110-1420-navi-앱얼림-logcat.txt`, 화면 `20261110-1427-navi-경로화면.png`). 실측은 `adb shell settings put global cached_apps_freezer disabled` 뒤 재부팅하고 했다. 실제 앱은 길안내 중에도 이벤트를 받으려면 포그라운드 서비스 같은 방법이 필요하다(미정).
- **내비 현재 위치:** 지도 앱은 안드로이드 위치가 아니라 GPS NMEA 문장을 읽는다. 에뮬레이터 `geo fix`가 만드는 NMEA는 체크섬이 늘 `*47`로 틀려 지도 앱이 버린다(`NMEA checksum mismatch: expected=47, actual=7D`). `geo nmea`로 체크섬을 맞춘 문장을 보내도 에뮬레이터가 다시 `*47`로 만들어 보낸다. 그래서 내비 현재 위치는 기본값(수정구 시흥동)에서 움직이지 않고, 도착·남은 거리 줄어듦을 에뮬레이터에서 볼 수 없다.
- **경로 재생(GPX/KML):** `adb emu geo`에는 `nmea`·`fix`·`gnss`뿐이고 경로 재생 명령이 없다(에뮬레이터 확장 창 GUI에만 있다). `geo fix`를 차례로 보내는 스크립트로 대신할 수 있지만, 내비 위치는 위 이유로 따라가지 않는다. FusedLocation은 따라간다.
- **지도 앱 설정:** 내비게이션 설정(충전소·경로·안내음·과속 경고·버전·데이터 삭제)에 모의 주행이 없고, 버전 정보를 10번 눌러도 숨은 메뉴가 없었다.

## 벨트 실측 (2026-11-10, 데모 `belt`)

콘솔 카탈로그에서 벨트 관련은 `safety.telltales`의 `SeatBelt` 경고등 하나뿐이다(좌석 구분 없음). 경고등을 On → Off → (뒷좌석 왼쪽 탑승) → Blink → Off로 바꾸며 SDK 값을 봤다. 원본 `20261110-1442-벨트실측.csv`.
- 좌석별 착용(`getCurrentSeatBeltBuckledState`·리스너): 늘 false.
- 미착용 경고(`hasSeatBeltNotBuckledWarning`): 속성 없음.
- 경고등(`getTelltalesInfo`·`registerTelltales`): 콘솔을 그대로 따라온다. 단 `PRIVATE_CAR_SAFETY` 권한이 필요하다.
- 차일드락의 "주행 중 뒷좌석 벨트 풀림" 시연 수단은 정해야 한다. ① 콘솔 벨트 경고등을 "뒷좌석 벨트 풀림"으로 읽기(실제 SDK 값이지만 좌석을 모르고 PRIVATE 권한) ② Pleos 차에도 데모 `belt` 입력을 만들기(조작) 중 하나.

## 앱 얼림 대응과 시연 이동 (2026-11-11, navi/live)

- **깨어 있기 서비스:** (2026-11-12 바꿈: 아래 시나리오 ① 실측 "앱 얼림" 행. 지금은 Pleos 차에서 앱 화면이 뜰 때 켜고 끄지 않는다) 처음에는 길안내 중이거나 루틴이 진행 중이면 `KeepAwakeService`(포그라운드 서비스, 종류 `specialUse`, 알림 하나)를 켰다. 에뮬레이터 `cached_apps_freezer`를 기본값으로 되돌리고 재부팅한 뒤 시험했다.
  - 서비스 켜짐: 내비 화면이 앞에 있어도 앱이 얼지 않고(`isFrozen=false`, `isForeground=true`), 경로 시작·경유지 이벤트와 근접 알림이 바로 왔다. 주유소로 geo fix를 옮기고 약 2초 안에 장소 도착·경유지 도착
  - 서비스 꺼짐(안내 취소 뒤 내비 화면을 앞에 둠): 앱이 얼어 있어(`isFrozen=true`) 유치원 도착이 20초 넘게 오지 않다가, 앱을 앞으로 가져온 순간 왔다
  - 경유지를 더하면 내비가 경로를 취소했다 다시 시작해서 길안내 상태가 1초 남짓 꺼진다. 그때 서비스를 끄니 앱이 뒤에 있어 다시 켜지 못했다(`startForegroundService() not allowed due to mAllowStartForeground false`). 그래서 이유가 사라져도 15초 기다렸다 끄고, 경로 요청 전에(앱이 아직 앞에 있을 때) 길안내 중으로 먼저 올린다
- **경로 시작 시간:** 이번에는 `requestRoute` 뒤 `onRouteStarted`까지 약 10초 걸렸다(어제 실측은 1초 안). 원인은 확인하지 못했다.
- **시연 이동 스크립트:** `tools/scene-move.ps1 -Scenario 1|2`. geo fix를 1초마다 직선으로 옮기고(기본 17m = 60km/h), 출발하면 콘솔로 D·60km/h, 도착하면 0km/h·P. 장소 좌표는 앱과 같은 `assets/places.json`. 시나리오 ① 1,585m 94초, ② 913m씩 왕복.
- **권하기 대기:** 같은 루틴은 10분에 한 번만 권한다(routines `suggestCooldownMs`). 리허설을 이어서 하면 두 번째는 권하지 않으니 앱을 다시 켠다.
- 원본: `20261111-1217-시나리오1-logcat.txt`, 캡처 `20261111-1217-시나리오1-세차권하기.png`, `20261111-1208-주유소도착-세차권하기.png`

## 시나리오 ① 세차 실측 (2026-11-11, routine/wash)

시나리오 ① 출장 가는 길(대사집 S1~S5)을 Pleos 에뮬레이터에서 처음부터 끝까지 돌리며 잰 것. 실제 경로(Pleos 차)와 안전 경로(가짜 차, 데모 `fake on`) 모두 S1~S5가 대사집 문장 그대로 나왔다(시연 모드 [다음 장면], adb `scene S5`, `tools/talk.ps1`).

| 무엇 | 결과 | 시연 표기 | 메모 |
|---|---|---|---|
| 첫 경로 시작 시간 | 에뮬레이터를 켠 직후 첫 `requestRoute` → `onRouteStarted` 24.5초. 그다음부터는 1초 안 | 실제 | 기다리다 다시 `requestRoute`를 보내면 내비가 앞 계산을 취소(`onRouteCancelled`)하고 처음부터 다시 해서 더 늦어졌다. `PleosNavi.ROUTE_MS`를 15초 → 45초로 늘리고 다시 요청하지 않는다 |
| 경로가 없을 때 `cancelRoute` | 지도 앱이 앞으로 나와 앱 화면을 가림 | — | 시연을 처음으로 돌릴 때는 길안내 중일 때만 취소한다(`AppGraph.restartTrip`) |
| 콘솔 세션 신호가 끊긴 뒤 제어 | 창문·와이퍼는 Done, 내기 순환 Failed, 문 잠금 3초 응답 없음 | 실제(신호 유지) | 신호(`POST /browser/session`)를 1초마다 보내는 동안은 넷 다 Done. debug 빌드 시연 모드(`ScenePlayer`)와 `tools/talk.ps1`이 신호를 보낸다 |
| 앱 얼림 | 지도 앱이 앞에 있고 길안내·루틴이 없을 때 앱이 얼어(`isFrozen=true`) 주유소 근접 알림을 못 받음 | 실제(깨어 있기 서비스) | 리허설 에뮬레이터는 `adb shell settings put global cached_apps_freezer disabled` 뒤 재부팅했다. 2026-11-12 app/wash: Pleos 차에서 앱 화면이 뜰 때 `KeepAwakeService`를 켜고 끄지 않게 바꿨다. 얼림 기본값(`device_default`)에서 지도 앱을 앞에 두고 길안내 없이 40초 기다려도 `isFrozen=false`, 주유소로 geo fix하자 바로 장소 도착. 에뮬레이터 설정을 바꿀 필요가 없다 |
| TTS | 엔진은 `ai.pleos.playground.caas` SpeechService 하나. 앱 `TextToSpeech` 초기화가 -1로 실패해 소리 없음 | 조작(녹음본) | `tools/make-voice.ps1`이 대사집 문장을 Windows 한국어 음성(Microsoft Heami)으로 `assets/voice/<문장 MD5>.wav`에 만들고, `AndroidSpeaker`가 녹음본을 `MediaPlayer`(USAGE_ASSISTANT)로 재생한다. 재생 중 `dumpsys audio`에 `state:started` 확인. 녹음본이 없는 문장은 소리가 없다 2026-11-12 app/wash: 녹음본과 TTS를 한 줄(`VoicePlayer`)에 세워 들어온 순서대로 낸다. 녹음본이 없는 말은 앞 녹음본이 끝날 때까지 기다렸다가 `소리 없이 건너뜀(녹음본 없음)`을 logcat에 남긴다(빠진 녹음본 찾기). 시연 [처음]·장면 건너뛰기 때는 나오던 말을 끊는다 |
| 문 열기(콘솔) | `PUT /vehicle/body?door_rl=true\|false`가 앱 `doorOpen`에 바로 반영 | 실제(콘솔로 장면 준비) | 세차 모드는 문이 열려 있으면 시작하지 않는다("문을 모두 닫으시면 시작할게요.", 세차 모드 기능 카드 조건) |
| 뒷좌석 탑승(콘솔) | `PUT /vehicle/seat?kind=occupied&area=rear_left&value=true|false`로 콘솔 `seat.occupied.rear_left`가 바뀌고, 앱은 뒤집어 읽어(탑승 행) 뒷좌석 탑승으로 본다(2026-11-12) | 실제(콘솔로 장면 준비) | 시나리오 ② S8·S11·S13·S15 장면 준비(`ScenePlayer.prepare`). 차일드락은 뒷문이 닫히는 순간 뒷좌석에 사람이 있으면 권하고, 뒷좌석이 비고 문이 닫히면 되돌린다 |
| 화면 읽기(uiautomator) | 화면 전환 중에는 `null root node`로 실패 | — | 자동 버튼 누르기는 실패하면 1초 뒤 다시 읽는다. 그래픽 오류(`Failed to find ColorBuffer`) 뒤 화면이 검게 멈추면 에뮬레이터를 다시 켠다 |

## 화면 1차와 시나리오 ① 다듬기 (2026-11-12, screen/scenario1)

docs/SCREEN.md 1장 화면을 앱에 옮기고 3장 ①-2~①-11(대사집 S1~S10)을 Pleos 에뮬레이터에서 돌리며 잰 것. 캡처는 `screen/`.

| 무엇 | 결과 | 시연 표기 | 메모 |
|---|---|---|---|
| 왼쪽 구역 그리기(1차, 2차에서 바뀜: 아래 화면 2차 표) | `SYSTEM_ALERT_WINDOW` 허가(`adb shell appops set com.westone.sunstone SYSTEM_ALERT_WINDOW allow`) 뒤 `TYPE_APPLICATION_OVERLAY` 창이 시스템 주행 화면(`ai.umos.drivingview`, 0~853) 위에 보이고 터치를 받음. 내비가 앞에 있어도 보임 | 조작(허가를 adb로 줌) | 데모 `overlay show`로 확인(`01-overlay-app.png`, `02-overlay-navi.png`). 오버레이 창의 위·아래 기준은 상태줄(90)·하단 막대(140)를 뺀 영역이다. 패널은 그 패널의 말이 시작될 때 띄운다(말에 그때의 질문을 붙여 두고 재생이 시작되면 알린다). 패널이나 듣는 중 막대가 떠 있는 동안은 창을 왼쪽 구역 전체(853×1210)로 키우고 불투명 배경(시안 `.drive` 그라데이션)을 칠해 주행 화면 내용(차 그림·빠른 버튼·미디어 줄)을 가린다. 아무것도 없으면 마이크 칸 크기로 줄인다. 주행 화면의 운전대 표시 아이콘(오른쪽 위)은 시스템 창이라 가릴 수 없다. 실측 화면 x 766~795, y 106~160(운전대 그림 28×28과 그 아래 막대 둘, 모든 장면 같은 자리)이라 패널 위 끝을 그 아래 16(y 177)으로 내려 겹치지 않게 했다(r6). 실제 차에서 일반 앱이 이 허가를 받을 수 있는지는 미확인. 허가가 없으면 앱 화면 안 왼쪽에 같은 패널을 그린다 |
| 운전대 음성 버튼 | SDK `SteeringWheel`은 각도·열선만 있고 버튼 API가 없음. Virtual Console에도 없음(기어·속도·문·공조·하이패스만). `input keyevent KEYCODE_VOICE_ASSIST`·`cmd car_service inject-key 231`은 앱 창이 포커스여도 시스템 음성 비서(`[GAA]`, Gleo)가 가져가 STT를 시작함 | 조작(마이크 칸) | `KEYCODE_SEARCH`·`HEADSETHOOK`는 앱에 오지만 운전대 버튼이 아니다. docs/SCREEN.md 5장 대안으로 마이크 칸(112)을 둔다. 처음에는 왼쪽 아래에 뒀으나 주행(D) 화면의 시스템 빠른 버튼 첫 칸을 가려(r3-S10-toast) 시안 최신본대로 상태줄 바로 아래 왼쪽으로 옮기고, 패널·막대가 떠 있는 동안은 숨긴다. P·D 모두 시스템 버튼을 가리지 않음(r4-P-idle, r4-S10-toast) |
| 음성 인식 | 에뮬레이터에 마이크 입력이 없다 | 조작(시연 버튼·adb `say`) | 마이크 칸·질문은 듣는 중 막대만 띄우고, 말은 시연 대사 버튼과 데모 `say`가 `AppGraph.hear()`로 넣는다. 들은 말은 막대에 HEARD로 보인다 |
| 화면 밀도 | 2560×1440, 160dpi | 실제 | 시안 1단위 = 1dp = 1px. 글자는 sp(글자 배율 1.0) |
| 세차 입구 판단 | 주유소 안 두 번째 정차를 세차 입구로 봄 | 조작(장면 준비 S8) | 주유소 첫 P는 주유 정차(토스트만), 속도 > 0 뒤 다시 P·N이면 "세차 모드 켤까요?". S8 준비가 D·5km/h → 0·P를 넣는다 |
| 질문 녹음본 | 새 문장 8개(S1~S10)를 `tools/make-voice.ps1`로 다시 녹음 | 조작(녹음본) | 3회 연속 실행에서 `소리 없이 건너뜀` 0건 |
| 3회 연속 | S1~S10을 `scene next`·`say`로 3번 이어 돌림. 회마다 앱 말 8개가 같은 순서로 나오고 끊김 없음(토스트는 2회차 캡처로만 확인) | — | 회마다 처음에 `scene restart`(말 끊기, 질문·거절 기록 지우기, 켜진 루틴 되돌리기, 길안내 취소) |

## 화면 2차와 시나리오 ① 다시 짜기 (2026-11-13, screen/scenario1)

docs/SCREEN.md 6장(2차 시안)대로 카드·판을 다시 짜고 대사집 S1~S9를 Pleos 에뮬레이터에서 돌리며 잰 것. 캡처는 `v2-*`.

| 무엇 | 결과 | 시연 표기 | 메모 |
|---|---|---|---|
| 왼쪽 구역 창 | 가림막을 없애고 창을 셋으로 나눔. 마이크 칸 창(위 왼쪽 112×112), 카드 창(x 12, 상태줄 아래 74, 829×카드 높이), 판 창(아래 12, 829×136). 둘레 12는 그림자 자리 | 조작(허가를 adb로 줌) | 카드·판 자체는 좌우 24, 위 176(상태줄 아래 86, 운전대 표시 아이콘 아래 끝 160에서 16), 아래 24, 폭 805. 그릴 것이 없는 창은 숨기고(`GONE`) 터치를 받지 않는다 |
| 카드 밖 터치 | 카드가 떠 있는 동안 카드 밖 시스템 창문 버튼을 누르면 logcat `FloatingControls window button click`, 아래 빠른 버튼은 `ACTION_OPEN_GLOVE_BOX`. 둘 다 시스템 화면이 받음 | 실제 | `input tap`으로 확인(2026-11-13). 그림자 둘레 12와 판이 떠 있는 동안 판 자리는 우리 창이 받는다 |
| 터치 안 받는 창의 불투명도 | `FLAG_NOT_TOUCHABLE`인 다른 앱 오버레이 창은 비쳐 보였다. 토스트 판 뒤의 미디어 줄이 보임(첫 v2-S3) | — | Android 12의 다른 앱 창 터치 통과 규칙(불투명도 0.8 이하)으로 보인다. 그래서 보이는 창은 모두 터치를 받게 했다 |
| 세차 입구 5초 | "곧 세차 모드 켤게요." 카드의 [지금 켜기]가 5초 차오르고, 대답이 없으면 5초 뒤 켜짐 | 실제(판단은 routines) | 5초 판단은 `RoutineRunner`(테스트에서 시간을 앞당겨 확인). 화면은 차오르기만 그린다. "취소"·"하지 마"·[취소]면 켜지 않는다 |
| 경유지 넣기 시간 | 대답 → `onWaypointChanged` 약 9초. 그동안 판은 들은 말과 "경유지에 넣고 세차를 예약할게요"를 두고 마이크 칸 자리에 도는 표시(`Asking.working`). 끝나면 그 자리가 토스트 "경유지에 넣고 세차를 예약했어요"로 바뀐다 | 실제 | 내비가 경로를 다시 찾는 시간. 20초 안에 답이 없으면 "경유지를 넣지 못했어요" 토스트와 logcat(`Sunstone` W "경유지 넣기 20초 넘김: …")을 남기고 세차를 예약하지 않는다 |
| 다음 장면(시연) | 마이크 칸·판의 마이크 칸·카드 앱 아이콘을 0.8초 누르면 `SceneControls.next()` | 조작(debug 빌드만) | 카드·판이 떠 있으면 마이크 칸이 숨어서 카드 앱 아이콘을 쓴다. 장면을 넣는 동안(준비·운전자 대사·앱 답 기다림)은 logcat `준비 중이라 하지 않음` |
| 운전자 대사 | 운전자 녹음본을 낸 뒤 들은 말로 넣음(S1, S3) | 조작(녹음본) | PC 한국어 음성이 Heami 하나라 같은 음성의 WAV 표본율을 0.84배로 적어 낮고 느리게 만든다(`tools/make-voice.ps1`). debug assets/driver, 앱 말 기록(logcat "말:")과 따로 재생 |
| 녹음본 | 앱 13개(새 문장: 주유·세차 묻기, "곧 세차 모드 켤게요.", "사이드미러 펴졌는지 확인해 주세요."), 운전자 2개 | 조작(녹음본) | 3회 연속 실행에서 `소리 없이 건너뜀` 0건 |
| 3회 연속 | 길게 누르기만으로 S1~S9를 3번 이어 돌림. 회마다 장면 9·앱 말 5·운전자 대사 2·토스트 3이 같은 순서, 실패 0 | — | 회마다 처음에 adb `scene restart`. v2-loop1~3-log.txt |

## 부르면 나오는 카드와 제출 빌드 (2026-11-14, app/scenarios)

앱 화면을 없애고 왼쪽 구역 카드만 쓰게 바꾼 뒤 Pleos 에뮬레이터 v2.1.0에서 잰 것.

| 확인한 것 | 결과 | 실제/조작 | 메모 |
|---|---|---|---|
| 하단 막대 아이콘으로 부르기 | 왼쪽에 부르는 카드가 뜨고 오른쪽 내비 화면은 그대로. 하단 막대에 Sunstone 아이콘이 남는다 | 실제 | `MainActivity`가 투명 테마로 떴다가 바로 닫힌다 |
| 첫 실행 권한 요청 | 위치·속도·에너지·알림을 차례로 묻는다. 묻는 동안은 시스템이 왼쪽 구역 창을 가리고, 답한 뒤 부르는 카드가 보인다 | 실제 | 권한 창은 오른쪽 앱 영역에 뜬다 |
| 카드 높이 | 위 176 ~ 미디어 줄 위 16(화면 1104), 높이 928 | 실제 | 측정 — 미디어 줄 위 끝 y 1120(높이 180). 시안은 160으로 그렸다 |
| 듣는 중 줄 | 카드 맨 아래 높이 88. 카드 없이 결과만 알릴 때는 카드 자리(위 176)에 줄만 뜬다 | 실제 | 켜짐 표시가 있으면 그 아래에 붙는다 |
| 켜짐 표시 중 부르기 | 켜짐 표시가 마이크 칸 자리를 가려 마이크 칸이 숨는다. 카드 앱 아이콘을 누르면 부르는 카드가 뜬다 | 실제 | 하단 막대 아이콘으로도 된다 |
| release APK 시연 | debug를 지우고 `apk/westone_Sunstone_release.apk`만 설치해 카드의 "시연"으로 ① S1~S9, ② S10~S18, ③ S19~S25를 끝까지 돌림 | 조작(시연 모드) | 왼쪽 구역 창 허가는 adb `appops` |
