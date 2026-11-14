# 카카오·오피넷·Open-Meteo 통합 기록

최신 변경: 오피넷과 가격 비교는 제거했고 카카오 주변 주유소 검색으로 대체했다.
현재 동작은 [시나리오 ① 기록](SCENARIO_ONE.md)을 기준으로 한다. 아래는 이전 통합 작업의 기록이다.

후속 작업에서 Pleos를 설치하고 앱·TTS·카카오 후보 화면·기존 내비 경로 시작을 확인했다.
현재 기기 상태와 실행 방법은 [에뮬레이터 테스트 기록](EMULATOR.md)을 참고한다.
아래 표의 기기 미검증 항목은 초기 통합 검증 시점의 기록이다.

2026-11-14. 현재 브랜치 `voice/pleos-tts`의 기존 날씨 작업에 통합했다.
코드 구현과 기기에서의 실제 성공 여부는 아래에서 구분한다. 커밋·푸시는 하지 않았다.

## 현재 동작

1. 텍스트 목적지 요청을 기존 해석기가 처리한다.
2. 카카오 장소 검색 결과가 하나면 기존 Pleos 내비에 이름·주소·좌표를 전달한다.
3. 여러 후보면 화면 또는 번호 입력으로 선택받은 후 길안내를 시작한다.
4. 검색 실패하면 출처를 안내하고 기존 등록 목적지만 대체 검색한다.
5. 연료 25% 이하·정차 중이면 현재 위치의 오피넷 반경 5km 검색을 수행한다.
6. 가격순 최대 다섯 후보의 실제 유종 가격·기준시각·세차 시설을 상세 조회한다.
7. 세차 시설이 확인된 후보를 우선하고, 없으면 주유만 추천한다.
8. 세차 시기라면 선택 주유소 좌표의 Open-Meteo 향후 168시간 예보를 평가한다.
9. 비 없음/비 있음/확인 불가에 따라 대사·버튼·예약을 함께 분기한다.
10. 사용자 동의 후에만 Pleos 경유지를 추가하고 해당 주유소의 도착·이탈을 감지한다.

별도 길찾기 API는 추가하지 않았다. 실제 도로 경로상 최소 우회는 판단하지 않으며 실시간 안내는
“현재 위치 주변에 ○○주유소가 있어요”라고 한다. 최저 우회·경로상 주유소라고 표시하지 않는다.

오피넷 세차 필드는 시설 존재 여부만 뜻한다. 자동세차·현재 영업·업체 예약을 의미하지 않는다.
실제 가격이 없거나 0원이거나 기준시각이 유효하지 않으면 후보에서 제외한다.
가격 기준시각이 미래이거나 7일보다 오래된 자료도 제외한다. 이 기간은 앱의 임시 유효성 정책이다.
한 후보 상세 조회 실패는 다른 유효 후보를 지우지 않는다. 모든 후보가 실패하면 주유소 추천을 보류한다.

## 단계별 수정·검증

| 단계 | 주요 수정 | 검증 결과 |
|---|---|---|
| 1. 로컬 설정·HTTP | ApiKeys, ApiHttp, 설정 예시·적용 도구 | 키 파일 Git 제외 확인. 키를 BuildConfig·APK·URL 로그에 넣지 않음 |
| 2. 카카오 검색 | KakaoPlaces, PleosNaviPort, TripDialog, 후보 UI | 모의 복수 후보·선택 좌표 테스트 통과. 실제 검색·좌표 변환 테스트 통과 |
| 3. 오피넷 | OpinetStations, LiveInfo, 모델 확장 | 모의 휘발유·경유·가격 누락·시설 불명 테스트 통과. 실제 서비스는 빈 목록 |
| 4. 실제 좌표 도착 | SelectedStation, Proximity, 내비 어댑터 | 등록·교체·해제·이전 콜백 무시·등록 실패 테스트 통과. 기기 GPS 검증 미실행 |
| 5. 날씨·세차 | 기존 Open-Meteo 재사용, TripDialog·문장 틀 확장 | A/B/C·주유만·시설 미확인·예약 금지·기존 1~7단계 단위 시나리오 통과 |
| 6. 회귀·빌드 | routines·app 테스트, debug APK | 로컬 테스트 111개 통과, 외부 테스트 3개 별도 실행 항목으로 건너뜀. APK 성공 |

## 실제 API 호출 결과

사용자가 제공한 키를 사용하라는 후속 지시에 따라 Git 제외 `local.properties`에 설정하고 호출했다.
문서·소스·로그에 키 값은 기록하지 않는다.

| 항목 | 실제 결과 |
|---|---|
| 카카오 강남역·파라다이스 호텔 부산·서울역 검색 | 성공. 이름·주소·WGS84 후보 확인 |
| 카카오 KATEC(KTM) → WGS84 및 왕복 변환 | 성공. 공식 개발자 포럼 사례와 대조 |
| 오피넷 주변 주유소 | HTTP 응답은 받았지만 `RESULT.OIL`이 빈 목록 |
| 오피넷 공식 예제 좌표 주변 검색 | 동일하게 빈 목록 |
| 오피넷 전국 평균 유가·공식 예제 주유소 상세 | 모두 빈 목록. 좌표 문제만으로 설명되지 않음 |
| 카카오·오피넷 실제 인증 테스트 | 2개 중 카카오 1개 통과, 오피넷 1개 실패(실제 가격 후보 없음) |
| Open-Meteo 실제 지역 검색 | 독립 HTTP 요청 성공 |
| Open-Meteo 실제 Forecast | 현재 실행에서 Java·Windows HTTP 모두 시간 초과 |
| Pleos 실제 길안내·경유지·도착 | 미검증. 연결 기기 없음, 로컬 Pleos AVD 없음 |

오피넷 빈 응답의 원인이 키·서비스 접근 상태·제공 데이터 상태 중 무엇인지는 확정하지 않았다.
오피넷 연결 완료·실제 가격 조회 성공이라고 보고하지 않는다. 빈 응답을 고정 가격으로 대체하지 않는다.
실제 서버 테스트는 데이터가 없으면 실패하도록 남겨 두었다.

Open-Meteo 시간 초과를 TLS 문제로 단정하지 않고 독립 Java·Windows 요청으로 분리 확인했다.
테스트에만 적용해 본 TLS 설정은 문제 해결 근거가 없어 제거했다. 인증서 검증도 우회하지 않았다.
예보 실패 시 UNKNOWN으로 세차 추천을 보류하고 주유 추천 자체는 유지한다.

## 기존 기능과 도착 감지

기존 Pleos Vehicle·NaviHelper·FusedLocation·ON_DEVICE TTS를 그대로 사용한다.
VoicePlayer·차일드락·시트·세차 차량 제어 구현은 이번 통합에서 변경하지 않았다.

실제 선택 주유소 좌표에 반경 150m의 ENTER·EXIT를 등록하고 장소 상태를 GAS_STATION/NONE으로
전달한다. 목적지·주유소를 재선택하거나 시연을 재시작하면 이전 등록을 해제한다.
고정 장소의 EXIT가 실제 주유소 안 상태를 덮지 않도록 조립부에서 우선순위를 처리한다.
반경 진입은 세차장 입구 인식이 아니다. 기존 첫 정차·이동 후 두 번째 정차 휴리스틱은 유지한다.

내비 경유지 추가가 성공했더라도 도착 감지 등록이 실패하면 세차 예약은 보류하고 이유를 표시한다.
검증 중 이 실패 경로가 필요함을 확인해 경로 성공과 도착 감지 성공을 별도 상태로 전달하도록 했다.
Proximity의 등록 대기 취소 후 늦게 등록되는 알림도 해제하도록 보완했다.

세차 모드 5초 카운트다운·취소, 창문·와이퍼·내기순환·문 잠금, D단 복원은 기존 동작이다.
전체 시나리오의 기기 시연 성공은 아직 확인하지 못했다. 단위 시나리오 통과와 혼동하지 않는다.

## 시연 모드·유종

현재 에뮬레이터는 전기차다. Sunstone 시연에서는 휘발유 차량을 가정하며 화면에 표시한다.
기존 연료 18%·마지막 세차 36일 설정은 기존 S1 장면 준비를 유지한다.
`sunstone.fuel.code=B027`은 휘발유, `D047`은 경유다. 하드웨어의 차종을 바꾸지는 않는다.

- `weather live`: 카카오·오피넷·Open-Meteo 실시간 경로
- `weather dry/rain/unknown`: 기존 고정 주유소와 지정 예보로 반복 가능한 debug 시연
- 일반 텍스트 날씨 질문: 기존과 같이 실제 Open-Meteo 사용

고정 모드는 실제 가격·예보가 아니라고 안내·표시한다. 실시간 API 실패 시 자동 진입하지 않는다.
실시간 목적지 후보 선택이 필요하면 기존 자동 장면 버튼만으로 진행되지 않고 사용자가 선택해야 한다.

## 설정·실행

1. Git 제외 `local.properties`의 다음 항목을 설정한다. 현재 사용자가 지정한 값은 로컬에만 적용했다.

```properties
sunstone.kakao.restKey=<local-only>
sunstone.opinet.apiKey=<local-only>
sunstone.fuel.code=B027
```

2. debug APK 설치·앱 1회 실행 후 `tools/configure-api.ps1`을 실행한다.
   이 도구는 로컬 설정을 현재 Android 사용자의 앱 비공개 `files/api.properties`로 공급한다.
   키는 프로세스 표준입력으로 전달하고 명령줄·출력·APK에는 넣지 않는다.
   스크립트 문법 검사는 통과했지만 기기가 없어 실제 적용 실행은 미검증이다.
3. 실제 인증 API 테스트는 기기 없이도 로컬 키로 실행할 수 있다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:SUNSTONE_LIVE_APIS = '1'
.\gradlew.bat -I tools/weather-build.init.gradle :app:testDebugUnitTest --tests '*ExternalLiveApiTest'
```

일반 회귀는 외부 서비스 가용성과 분리한다. 실제 API 실패 결과를 별도로 기록하며 숨기지 않는다.

```powershell
Remove-Item Env:SUNSTONE_LIVE_APIS -ErrorAction SilentlyContinue
Remove-Item Env:SUNSTONE_LIVE_WEATHER -ErrorAction SilentlyContinue
.\gradlew.bat -I tools/weather-build.init.gradle :routines:test :app:testDebugUnitTest :app:assembleDebug
```

최종 결과는 routines 97개 통과, app 17개 중 14개 통과·외부 테스트 3개 건너뜀, 실패 0이다.
별도 실제 인증 테스트의 오피넷 실패와 별도 Forecast 시간 초과는 위 표대로 남는다.
검증 APK는 `build/integration/app-debug.apk`, 로그는 `build/integration/build.log`,
실제 인증 API 로그는 `build/auth-api-live.log`에 있다. 모두 Git 제외 산출물이다.

앱 비공개 로컬 파일도 배포용 비밀 관리의 최종 해법은 아니다. 상용 배포에는 키를 서버에 두는
백엔드 프록시가 적합하다. 이번 범위에는 백엔드 배포·별도 길찾기 API를 추가하지 않았다.

## 이번 통합의 수정·추가 파일

- `.gitignore`, `local.properties.example`: 로컬 키 파일 제외·설정 예시
- `app/build.gradle.kts`: 테스트에 로컬 설정 경로만 전달
- `app/.../info/ApiKeys.kt`, `ApiHttp.kt`: 비공개 설정·인증 HTTP
- `app/.../info/KakaoPlaces.kt`: 실제 목적지 후보·좌표계 변환
- `app/.../info/OpinetStations.kt`, `LiveInfo.kt`: 실제 주유소·유종 가격·시설 제공
- `app/.../navi/PleosNaviPort.kt`: 검색·주소 전달·실제 경유지 도착 연결
- `app/.../navi/SelectedStation.kt`, `Proximity.kt`: 동적 등록·수명·늦은 콜백 처리
- `app/.../SunstoneApp.kt`, `ui/SunstoneScreen.kt`: API 조립·번호/화면 선택·모드 표시
- `routines/.../model/Trip.kt`, `port/Ports.kt`: 주소·출처·유종·시설·후보·도착 상태 계약
- `routines/.../dialog/TripDialog.kt`, `speech/TripLines.kt`: 후보 선택·주변 안내·예약 분기
- `routines/src/test/.../TripScenarioTest.kt`: 후보·시설·가격 없음·예약 회귀
- `app/src/test/.../info/ExternalApiTest.kt`, `ExternalLiveApiTest.kt`: 모의 응답·실제 인증 검증
- `tools/configure-api.ps1`: 기기 비공개 설정 공급
- `README.md`, `docs/WEATHER.md`, `docs/INTEGRATION.md`: 최신 사용법·검증 기록

기존 날씨 작업 파일과 통합 작업은 같은 작업 폴더의 미커밋 변경으로 남아 있다.

## 공식 근거

- [카카오 장소 검색·좌표계 변환](https://developers.kakao.com/docs/ko/local/dev-guide)
- [오피넷 KATEC 좌표 변환에 대한 카카오 공식 답변](https://devtalk.kakao.com/t/api/136911)
- [오피넷 반경 검색](https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=3)
- [오피넷 상세정보·가격·시설](https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=1)
- [Open-Meteo Forecast](https://open-meteo.com/en/docs)
