# Open-Meteo 날씨 연동

이 문서는 초기 날씨 연동 단계의 기록이다. 주유소 고정 정보·키 설정 등 이후 변경과 현재 검증
상태는 [외부 API 통합 기록](INTEGRATION.md)을 기준으로 한다.

2026-11-14 구현·검증 기록. 기본 모드는 실제 API다. 카카오 검색과 STT 정식 연동은 포함하지 않는다.

## 동작

길안내 성공 → 기존 주유소 선택 → 마지막 세차 14일 이상 → 선택 주유소 좌표의 예보 조회 순서다.
부산 목적지로 안내하더라도 서울 주유소를 골랐다면 서울 주유소의 좌표를 조회한다.
주유소 목록·가격·세차 시설 여부는 기존 시연 고정값이며, 실제 주변·경로 검색이 아니다.

| 예보 | 음성 뒤 문장 | 버튼 | 세차 예약 |
|---|---|---|---|
| 비 없음 | 일주일간 비 예보가 없어서 세차도 추천드려요 | 주유하고 세차 / 주유만 | 세차 동의와 경유지 추가 성공 시만 |
| 비 있음 | 일주일간 비 예보가 있어서 세차는 추천드리지 않아요 | 주유소 경유 / 경유 안 함 | 없음 |
| 확인 불가 | 현재 비 예보를 확인할 수 없어 세차 추천은 보류할게요 | 주유소 경유 / 경유 안 함 | 없음 |

카드에는 실제/테스트 출처도 표시한다. 세차를 제안하지 않은 카드에서 “세차도 할게”를 말해도
주유 동의로 바꾸지 않고 재선택을 안내한다. 수동 세차 실행 자체를 날씨로 금지하지는 않는다.

주유소 첫 정차, 이동 후 두 번째 정차, 5초 실행·취소, 차량 설정 제어, D단 복원은 기존 구현이다.
창문은 다시 열지 않고 문 잠금도 풀지 않는다. 내비·TTS·차량 제어 구현 파일은 수정하지 않았다.

## 예보 판단

- `timezone=Asia/Seoul`, `forecast_days=9`로 여유 있게 요청한다.
- 요청 시점부터 향후 168시간과 겹치는 시간 구간을 평가한다. 과거 시각 행은 제외한다.
- 시간별 강수량·확률은 표시 시각 직전 한 시간의 값이다. 경계의 일부 시간도 해당 시간 구간
  전체를 보수적으로 평가한다. 분 단위 강수량을 추정하거나 정확한 강수 시각을 보장하지 않는다.
- 기간 전체의 시간 연속성·배열 길이·단위·필수값을 먼저 검사한다. 일부 누락도 UNKNOWN이다.
- 비·소나기 합계가 시간당 0.5mm 이상, 또는 비 형태 코드와 강수확률 60% 이상이면 비 예상이다.
- 확률은 눈도 포함한다. 눈만 예상되거나 확률이 높지만 형태를 확정하지 못하면 UNKNOWN으로
  세차 추천을 보류한다. 일반 날씨 질문에서는 확인한 눈 예보를 안내한다.
- 기준은 Sunstone 임시 추천 정책이다. Open-Meteo·기상청의 공식 세차 기준이 아니다.
- 같은 좌표의 원본 예보는 최대 10분 캐시한다. 판단 기간은 매번 다시 계산하고 완전성을 검사한다.
  오래된 캐시를 통신 실패 시 최신 예보처럼 사용하지 않는다.
- 연결 제한 3초, 읽기 제한 4초. 일시적인 통신 실패는 400ms 후 한 번 재시도한다.
  세차 조회 코루틴 제한은 16초이며 취소는 전파하고 최종 실패는 null이다.
  최신 기기 검증과 수정 내용은 [날씨 수정 기록](WEATHER_FIX.md)을 참고한다.

`InfoPort.rainAt(point, days)`를 추가했다. 기존 `rainWithin(days)`와 Boolean? 의미는 유지한다.
실시간 구현은 좌표 없는 요청에 null을 반환한다. 기존 가짜 구현의 새 메서드는 구형 메서드로
위임하므로 기존 테스트·시연 구현을 재사용할 수 있다.

## 텍스트 날씨 질문

기존 입력칸 또는 debug `say`로 사용한다. STT는 연결하지 않았다.

- `전주 날씨 알려줘`
- `부산 내일 비 와?`
- `서울 이번 주 비 소식 있어?`
- `여기 날씨 어때?`
- `오늘 비 와?`

지역이 있으면 Geocoding → Forecast, 없으면 FusedLocation의 새 위치 콜백 → Forecast 순서다.
지역명은 일부 행정구역 표기를 보완하지만 위경도는 API 응답에서 얻는다. 서울·부산·전주·제주는
실제 서버 검색을 확인했다. 후보가 여러 개면 번호·행정구역을 안내하고 다음 텍스트로 재확인한다.
검색 실패 때 임의 위치를 사용하지 않는다. 모든 한국 지명의 검색 성공을 보장하지 않는다.

현재 위치는 `LocationData.timestampMillis`가 현재부터 2분 이내인 새 콜백만 사용한다.
권한 거부·오래된 위치·타임스탬프 검증 실패·4초 내 위치 미수신은 현재 위치 확인 실패다.
여기의 날씨 조회 실패가 좌표를 가진 주유소의 예보 조회를 차단하지는 않는다.

오늘은 지금부터 자정, 내일은 다음 날 하루, 이번 주는 일요일 종료까지, 7일은 향후 168시간이다.
기본 날씨 답변은 강수 요약이며 시간별 최대 강수확률과 최대 비의 양을 안내한다.

## 실시간·재현 시연

아래 명령은 debug 빌드에만 있다. release 기본 모드는 실제 API이며 가짜 날씨 클래스를 포함하지 않는다.
일반 지역 날씨 질문은 재현 모드와 관계없이 항상 실제 API를 사용한다.

```powershell
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "weather live"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "weather dry"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "weather rain"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "weather unknown"
```

기본은 live다. `dry/rain/unknown`은 실제 예보가 아니라고 음성과 대화 기록으로 알리고 카드·로그에도
표시한다. 자동으로 가짜 맑음 응답으로 전환하지 않는다. 기존 S1~S9 전체 재현은 `weather dry`를
명시적으로 선택한 다음 시작한다. 실시간 비 예보가 있으면 세차 단계로 이어지지 않는 것이 정상이다.
테스트 모드는 현재 실행에만 적용되며 앱 재시작 시 live로 돌아간다.

## 검증 결과

JDK 21, Android compile SDK 35. OneDrive가 기존 build 산출물을 잠가 임시 산출물 경로로 검증했다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
# 실제 서버 테스트를 포함할 때만 설정. 일반 단위 테스트는 이 테스트를 건너뛴다.
$env:SUNSTONE_LIVE_WEATHER = '1'
.\gradlew.bat -I tools/weather-build.init.gradle :routines:test :app:testDebugUnitTest :app:assembleDebug
```

산출물은 `%TEMP%/sunstone-weather-build`에 있다. 검증한 APK·로그는 작업 폴더의
`build/weather/app-debug.apk`, `build/weather/build.log`에도 복사했다. 이 폴더는 Git 제외 대상이다.

| 검증 | 실제 결과 |
|---|---|
| routines 단위·시나리오 테스트 | 93개 통과, 실패·건너뜀 0 |
| app 단위·JSON·실제 서버 테스트 | 8개 통과, 실패·건너뜀 0 |
| debug APK | assembleDebug 성공 |
| 실제 Geocoding | 서울·부산·전주·제주 검색 성공 |
| 실제 Forecast | 주유소 37.4852, 127.0407 좌표, 216시간 자료·단위·기간 확인 |
| 에뮬레이터 | 미실행. adb 기기 목록 없음, 로컬 Pleos AVD·에뮬레이터 실행 환경 없음 |
| 새 녹음본 생성 | 실패. PC에 Microsoft Heami Desktop 음성 없음 |

검증에는 60%·0.5mm 경계, 눈 구분, 과거 시간 제외, 중복·역순·중간 누락·종료 부족,
JSON null·단위·시간대 오류, 잘못된 좌표, 통신 실패, 8초 시간 초과, 캐시 재사용,
주유만 경유·세차 미예약, 주유소 좌표 전달, 예보 대기 중 재시작 후 카드 폐기를 포함한다.
기존 세차·차일드락·시트 테스트도 통과했다. 기기에서의 위치·TTS·내비 종단 간 검증은 별도로 필요하다.

## 수정·추가 파일

- `app/build.gradle.kts`: JSON·코루틴 테스트 의존성, JVM 로그 스텁 설정
- `app/src/main/AndroidManifest.xml`: INTERNET 권한
- `app/src/main/java/com/westone/sunstone/SunstoneApp.kt`: 실제 정보 조립·텍스트 날씨 진입·재시작 무효화
- 같은 패키지의 `info/OpenMeteo.kt`: Forecast·Geocoding·HTTP·캐시·파싱
- `info/LiveInfo.kt`: 고정 주유소와 실제 예보 조합
- `info/WeatherLocation.kt`: 새 위치·측정 시각 검사
- `info/WeatherConversation.kt`: 지역 후보 재확인·일반 날씨 대화
- `app/src/debug/java/com/westone/sunstone/demo/DemoReceiver.kt`: 명시적인 모드 명령
- 같은 패키지의 `DemoWeather.kt`: debug 고정 예보
- `app/src/test/java/com/westone/sunstone/info/OpenMeteoTest.kt`: JSON·오류·실제 서버 테스트
- `routines/.../port/Ports.kt`: 좌표 기반 조회와 출처
- `routines/.../dialog/TripDialog.kt`: 3분기 카드·예약·오래된 응답 폐기
- `routines/.../speech/TripLines.kt`: 대사·버튼·근거 문장
- `routines/.../understand/PhraseMatcher.kt`: “응, 주유할게” 처리
- `routines/.../weather/Forecast.kt`: 예보 모델과 순수 판단 정책
- `routines/.../weather/WeatherQuery.kt`: 텍스트 요청·기간 해석
- `routines/.../script/ScriptBook.kt`: 승인된 비 없음 대사 표현 반영
- `routines/src/test/.../TripScenarioTest.kt`, `WeatherTest.kt`: 분기·기간·회귀 테스트
- `tools/weather-build.init.gradle`: OneDrive 잠금 우회용 산출물 경로
- `tools/make-weather-voice.ps1`: 기존 WAV를 지우지 않는 선택적 A/B/C 녹음본 추가 도구
- `README.md`, `docs/WEATHER.md`: 사용·검증·제한·출처

## 남은 제한

Pleos TTS 큐와 기존 녹음본 대체 구현은 그대로다. 동적인 지역·숫자 문장과 새 A/B/C 문장에는
새 녹음본이 없으므로 TTS 실패 시 화면·대화 기록만 남을 수 있다. 새 기본 시연 대사 녹음본은
한국어 음성이 설치된 Windows에서 `tools/make-weather-voice.ps1`로 추가할 수 있다.

뒷문 차일드락·시트 센서·주유소 가격·일반 목적지 검색의 기존 한계는 그대로다.
기기에서 날씨 응답 대기 화면, 후보 선택, 현재 위치, 새 TTS 대사와 5초 세차 흐름을 확인해야 한다.
새로운 “비 와도 세차” 예약 대화는 추가하지 않았다. 상업 배포 권한을 확보했다는 의미도 아니다.

## 사용 조건·출처

무료 공개 API는 비상업 프로토타입용이며 키가 필요 없다. 상업 배포에는 해당 유료 조건을 검토한다.
날씨 데이터 출처는 Open-Meteo, 지역 데이터 출처는 GeoNames다. 데이터 출처·가공 사실을 표시한다.

- [Forecast 공식 문서](https://open-meteo.com/en/docs)
- [Geocoding 공식 문서·GeoNames 출처](https://open-meteo.com/en/docs/geocoding-api)
- [무료·상업 사용 조건](https://open-meteo.com/en/pricing)
- [라이선스](https://open-meteo.com/en/licence)
