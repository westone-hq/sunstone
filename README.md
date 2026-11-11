# Sunstone

**현대차에 이미 있지만 잘 안 쓰는 기능을, 운전자가 편하게 쓰도록 대신 세팅해 주는 Pleos Connect 앱**

Pleos Connect 앱 · Android (Kotlin, Jetpack Compose) · 2026 현대자동차그룹 차량용 앱 개발 공모전

> 프로토타입입니다. "예정"·"개발 중"으로 표시한 것은 아직 동작하지 않습니다.

## 왜 만들었나

세차 모드, 차일드락, 시트 메모리처럼 현대차에는 잘 만든 기능이 많습니다. 그런데 설정 메뉴 깊숙이 있거나,
켜는 조건을 몰라서, 혹은 여러 개를 하나씩 눌러야 해서 거의 쓰이지 않습니다.
Sunstone은 그 기능들을 필요한 순간에 한 번에 세팅하고, 끝나면 원래대로 돌려놓습니다.

## 어떻게 동작하나

- **말 한마디로** — "세차할게" 하면 창문 닫기, 와이퍼 끄기, 내기 순환, 문 잠금을 한 번에 맞춥니다.
  지금은 말을 글자로 넣습니다(음성 인식은 개발 중).
- **상황을 보고 먼저** — 세차장에 도착하거나 뒷좌석에 아이가 타면 먼저 권하고, "네"를 받은 뒤 실행합니다.
  뒷좌석 탑승은 차에서 읽고, 주유소·유치원 도착은 위치 근접 알림으로 알아챕니다(장소는 지금 시연용 고정 목록).
- **끝나면 원래대로** — 바꾸기 전 값을 기억해 두었다가 그대로 돌려놓습니다.
- **못 하는 건 알려 주기** — 앱이 직접 할 수 없는 단계(사이드미러 접기, 이 차에 없는 선루프 등)는 짧게 말로 안내합니다.

| 루틴 | 하는 일 | 상태 |
|---|---|---|
| 세차 모드 | 창문 닫기, 와이퍼 끄기, 내기 순환, 문 잠금. 선루프·사이드미러는 안내. 세차장을 떠나면 원래대로 | 동작 (에뮬레이터 실측. 주유소 도착은 위치로 감지, 이동은 시연 스크립트) |
| 차일드락 | 뒷창 잠금, 문 잠금, 공조 켜기. 문 차일드락·뒷유리 블라인드는 안내(앱이 걸 권한이 없음). 주행 중 뒷좌석 벨트 확인 | 동작 (에뮬레이터 실측. 벨트 값은 시연 입력) |
| 시트 메모리 | 누가 앉았는지 묻고 그 사람 자세로 맞춤(운전석·조수석) | 개발 중 (두 좌석 모두 시간 방식 이동은 실측 완료) |
| 길안내 대화 | 목적지를 말하면 연료·주유소 가격·날씨·지난 방문을 보고 주유와 세차를 함께 제안 | 예정 (내비 경로·경유지·남은 거리·통행료는 에뮬레이터 실측 완료) |
| 하이패스 카드 확인, 뒷유리 블라인드 | 통행료 경로에서 카드 확인 안내, 후진 시 블라인드 | 예정 |

## 실행 방법

| 항목 | 내용 |
|---|---|
| APK | `apk/` (예정) |
| 테스트 환경 | Android Studio, JDK 17~21, Pleos Connect v2.1.0 에뮬레이터(API 34) |
| Android | minSdk 28, target/compileSdk 35 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3 |
| 필요한 설정 | 없음. 바깥 API(키는 `local.properties`, 키가 없으면 고정 응답)는 예정 |

1. Android Studio에서 이 폴더를 열고 Gradle 동기화를 기다립니다.
2. AVD 관리자에서 **Pleos Connect v2.1.0** 이미지로 에뮬레이터를 만들고 켭니다.
3. `app`을 실행합니다. 차 값은 Virtual Console에서 바꿉니다: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088

```
./gradlew :routines:test         # 기능 테스트
./gradlew :app:installDebug      # 앱 설치
```

시연 이동(에뮬레이터 GPS·속도·기어): `tools/scene-move.ps1 -Scenario 1` (서울 → 주유소), `-Scenario 2` (집 → 유치원 → 집)

시연용 장면 주입(debug 빌드):
```
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "place carwash"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "say 세차할게"
```

## Pleos Connect SDK 활용

| SDK | 쓰는 값 · 기능 | Sunstone에서 하는 일 |
|---|---|---|
| Vehicle (Gear, Door, Seat) | 기어, 문 열림, 좌석 탑승 | 정차·주차 판단, 뒷좌석 탑승 확인 |
| Vehicle (Window, Wiper, HVAC, Door) | 창문 위치·잠금, 와이퍼, 내기 순환, 공조 전원, 문 잠금 | 세차 모드·차일드락 실행과 되돌리기 |
| Vehicle (Seat) | 시트 축 이동(운전석·조수석) | 사람별 자세 맞추기 (개발 중. 위치를 읽을 수 없어 움직인 시간으로 맞춤) |
| Vehicle (Odometer, EvBattery) | 주행 가능 거리, 배터리 | 상태 줄 표시. 길안내 중 주유·충전 제안은 예정 |
| AAOS 차량 속성 | 속도, 연료 종류 | 주행 중 판단, 차 종류 표시 |
| NaviHelper | 경로, 경유지, 남은 거리·통행료 | 주유소 경유 (말로 시작하는 길안내 대화는 예정) |
| FusedLocation | 근접 알림(들어감·나감) | 주유소·유치원 도착 감지, 길안내 목적지·경유지 도착 |

API별 실측 결과와 시연에서 실제 값·조작 값을 어떻게 쓰는지는 [docs/api-spike.md](docs/api-spike.md)에 있습니다.

## 구조

```
routines/   순수 Kotlin. 루틴, 실행 엔진, 말 알아듣기, 가짜 차. 차 없이 테스트로 끝까지 돈다
app/        Pleos SDK 연결, 음성, 화면, 시연 입력
```
자세한 내용은 [docs/STRUCTURE.md](docs/STRUCTURE.md).

## 팀

박건준(앱 연결) · 박희정(기능 구현) — westone
