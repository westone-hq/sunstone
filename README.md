# Sunstone

**차에 이미 있는데 아무도 안 쓰는 기능을, 필요한 순간에 한 번에 켜고 끝나면 원래대로 돌려놓는 Pleos Connect 앱**

Pleos Connect 앱 · Android (Kotlin, Jetpack Compose) · 2026 현대자동차그룹 차량용 앱 개발 공모전

> 개발 중입니다. 아래 내용 중 "예정"으로 표시한 것은 아직 동작하지 않습니다.

## 한눈에

- **말 한마디로** — "세차할게" 하면 창문·선루프 닫기, 와이퍼 끄기, 내기 순환, 문 잠금을 한 번에. 앱이 할 수 없는 단계(미러 접기 등)는 정확히 말로 안내합니다.
- **상황을 보고 먼저** — 세차장에 도착하면 세차 모드를, 뒷좌석에 아이가 타면 차일드락 점검을 먼저 권합니다. 실행 전에는 항상 "네"를 받습니다.
- **끝나면 원래대로** — 실행 전 값을 저장해 두었다가 그대로 돌려놓습니다.
- **길안내 대화** (예정) — 목적지를 말하면 연료·주유소 가격·날씨·지난 방문을 보고 주유와 세차를 함께 제안합니다.

1차 루틴: 세차 모드, 차일드락, 시트 메모리 · 2차: 하이패스 카드 확인, 뒷유리 블라인드

## 실행 방법

| 항목 | 내용 |
|---|---|
| APK | `apk/` (예정) |
| 테스트 환경 | Android Studio, JDK 17, Pleos Connect v2.1.0 에뮬레이터(API 34) |
| Android | minSdk 28, target/compileSdk 35 |
| Pleos SDK | Vehicle 2.3.2 (예정: NaviHelper 3.0.5) |
| 필요한 설정 | 바깥 API 키는 `local.properties`에 둡니다. 키가 없으면 고정 응답으로 동작합니다 |

1. Android Studio에서 이 폴더를 열고 Gradle 동기화를 기다립니다.
2. AVD 관리자에서 **Pleos Connect v2.1.0** 이미지로 에뮬레이터를 만들고 켭니다.
3. `app`을 실행합니다. 차 값은 Virtual Console에서 바꿉니다: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088

```
./gradlew :routines:test         # 기능 테스트
./gradlew :app:installDebug      # 앱 설치
```

시연용 장면 주입(debug 빌드):
```
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "place carwash"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "say 세차할게"
```

## 구조

```
routines/   순수 Kotlin. 루틴, 실행 엔진, 말 알아듣기, 가짜 차. 차 없이 테스트로 끝까지 돈다
app/        Pleos SDK 연결, 음성, 화면, 시연 입력
```
자세한 내용은 [docs/STRUCTURE.md](docs/STRUCTURE.md), Pleos API 확인 결과는 [docs/api-spike.md](docs/api-spike.md).

## 팀

박건준(앱 연결) · 박희정(기능 구현) — westone
