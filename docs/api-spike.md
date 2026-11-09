# Pleos API 실측 기록

에뮬레이터: Pleos Connect v2.1.0 / Vehicle SDK 2.3.2. `tools/api-explorer`로 하나씩 눌러보고 Virtual Console 값과 비교한다.
결과: `반영` · `읽기값만` (다시 읽은 값만 바뀜·지연) · `실패` (실패·API 없음)

Virtual Console은 브라우저 세션이 열려 있을 때만 차량 쪽과 연결된다. 페이지를 열어 두거나
`POST /browser/session?client=<이름>`을 1초마다 보낸다(3초 끊기면 `emulator_inactive`).
읽기 판정 근거: Pleos 서비스 로그 `broadcastPropertyCallback(applicationId = com.westone.sunstone …)`와 앱 상태 줄.

| 루틴 | API | 우리 코드 호출 방식 | 결과 | 메모 |
|---|---|---|---|---|
| 세차 | getCurrentWindowPosition / setWindowPosition (ROW_1_LEFT…ROW_2_RIGHT) | 직접 | | 다른 팀: 반영 O, 0으로 되돌리기 실패 사례 |
| 세차 | setWindowPosition ROOF_TOP_1/2 (선루프) | 직접 | | |
| 세차 | getCurrentWiperSwitch / setWiperSwitch (OFF·AUTO) | 읽기 직접, 쓰기 리플렉션 | | |
| 세차 | getHvacRecirculationState / setHvacRecirculation | 직접 | | |
| 세차·차일드락 | setDoorLock (ROW_1/2 LEFT/RIGHT) / 잠금 상태 읽기 | 쓰기 직접, 읽기 리플렉션 | | |
| 차일드락 | getCurrentWindowLockState / setWindowLock (ROW_2_*) | 읽기 직접, 쓰기 리플렉션 | | 다른 팀: 읽기값만 true, 콘솔 false |
| 차일드락 | moveWindowBlind (REAR_WINDSHIELD) / 블라인드 위치 읽기 | 리플렉션 | | MoveControl 방향 의미 확인 필요 |
| 차일드락 | getHvacPowerState / setHvacPower (ROW_2_LEFT) | 직접 | | |
| 차일드락 | getCurrentSeatOccupancy / registerSeatOccupancy (ROW_2_*) | 직접 | 실패 | 2026-11-09 측정. 값은 오지만 반대로 읽힌다. 에뮬레이터는 AAOS 기준 1=빈자리, 2=탑승(`SEAT_OCCUPANCY` area 16/64)을 보내는데 SDK 2.3.2는 `값 == 1`을 탑승(true)으로 바꾼다. 콘솔 전부 빈자리일 때 앱은 운전석·조수석·뒷좌석 전부 탑승으로 표시 |
| 차일드락 | getCurrentSeatBeltBuckledState / registerSeatBeltBuckledState (ROW_2_*) | 직접 | | 2026-11-09 확인 못 함. Virtual Console 카탈로그에 좌석별 벨트 항목이 없다 |
| 시트 | getCurrentSeat{ForeAft,Height,BackrestAngle,Tilt,Depth,HeadrestHeight}Position | 리플렉션 | | 함수 이름 확인 필요 |
| 시트 | moveSeat{…}(area, FORWARD/BACKWARD/STOP) | 리플렉션 | | 높이·각도에서 FORWARD가 어느 쪽인지 |
| 시트 | selectSeatMemory(area, n) | 직접 | | 성공 콜백 없음 |
| 공통 | getGear().registerGearSelection | 직접 | 반영 | 2026-11-09 측정. P→D→R→P 모두 전달(`VENDOR_GEAR_SELECTION` 4/8/2/4), 앱 상태 PARK/DRIVE/PARK 확인. R에서는 후방 카메라가 앱을 가려 화면 확인은 못 함. deprecated, GEAR_SELECTION 속성 권장 – 실측 때 비교 |
| 공통 | getCurrentDoorPosition / registerDoorPosition | 직접 | 반영 | 2026-11-09 측정. ROW_2_LEFT/RIGHT 열기·닫기 모두 전달(`DOOR_POS` area 16/64, 1/0), 앱 상태 줄 일치 |
| 공통 | PERF_VEHICLE_SPEED (AAOS) | 직접 | | 에뮬레이터에서 못 넣으면 데모 `speed` |
