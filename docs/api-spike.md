# Pleos API 실측 기록

에뮬레이터: Pleos Connect v2.1.0 / Vehicle SDK 2.3.2. `tools/api-explorer`로 하나씩 눌러보고 Virtual Console 값과 비교한다.
결과: `반영` · `읽기값만` (다시 읽은 값만 바뀜·지연) · `실패` (실패·API 없음)

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
| 차일드락 | getCurrentSeatOccupancy / SeatBeltBuckledState (ROW_2_*) | 직접 | | Virtual Console Seat 카드 |
| 시트 | getCurrentSeat{ForeAft,Height,BackrestAngle,Tilt,Depth,HeadrestHeight}Position | 리플렉션 | | 함수 이름 확인 필요 |
| 시트 | moveSeat{…}(area, FORWARD/BACKWARD/STOP) | 리플렉션 | | 높이·각도에서 FORWARD가 어느 쪽인지 |
| 시트 | selectSeatMemory(area, n) | 직접 | | 성공 콜백 없음 |
| 공통 | getGear().registerGearSelection | 직접 | | Virtual Console Motion → Gear. deprecated, GEAR_SELECTION 속성 권장 – 실측 때 비교 |
| 공통 | getCurrentDoorPosition / registerDoorPosition | 직접 | | |
| 공통 | PERF_VEHICLE_SPEED (AAOS) | 직접 | | 에뮬레이터에서 못 넣으면 데모 `speed` |
