# Sunstone

**차에 이미 있지만 잘 쓰이지 않는 기능을, 필요한 순간에 운전자에게 먼저 권하고 말 한마디로 켜고 끄는 차량 루틴 앱**

Pleos Connect 앱 · Android (Kotlin, Jetpack Compose) · 2026 현대자동차그룹 차량용 앱 개발 공모전 · 팀 westone

> 프로토타입입니다. 무엇을 만드는지와 세부 규칙은 [docs/SPEC.md](docs/SPEC.md)에, 실제로 되는 것과 시연을 위해 값을 넣어 조작한 것은 아래 "되는 것과 조작한 것"과 [docs/api-spike.md](docs/api-spike.md)에 적었습니다.

## 한눈에

| 항목 | 내용 |
|---|---|
| 앱 이름 | Sunstone (`com.westone.sunstone`) |
| APK | [`apk/westone_Sunstone_release.apk`](apk/westone_Sunstone_release.apk) |
| 에뮬레이터 | **Pleos Connect v2.1.0** (시스템 이미지 Pleos Connect v2.1.0 APIs, API 34) |
| 화면 규격 | **2560×1440, 160dpi** (왼쪽 주행 화면 구역 853×1300, 상태줄 90, 하단 막대 140) |
| Android | minSdk 28, target/compileSdk 35 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1 |
| 빌드 도구 | Android Studio 2026.2.2, Gradle 8.10.2, Android Gradle Plugin 8.8.2, Kotlin 2.0.0, JDK 17~21 |

## 실행 방법

1. Pleos Connect v2.1.0 에뮬레이터를 켜고 APK를 설치합니다.
   ```
   adb install apk/westone_Sunstone_release.apk
   ```
2. 왼쪽 구역에 카드를 띄울 수 있게 허가합니다.
   ```
   adb shell appops set com.westone.sunstone SYSTEM_ALERT_WINDOW allow
   ```
   허가가 없으면 같은 카드가 왼쪽 구역이 아니라 앱 화면 안에 보입니다.
3. 하단 막대의 앱 목록에서 **Sunstone**을 누릅니다. 처음에는 권한(위치, 속도, 에너지, 알림, 마이크)을 묻습니다. 모두 허용합니다.
   Sunstone은 왼쪽 구역만 씁니다. 오른쪽은 앞에 있던 화면(내비)이 그대로 남고, 왼쪽에 **부르는 카드**(루틴 세 줄)가 뜹니다.
   그다음부터는 왼쪽 위 **마이크 칸**이나 하단 막대의 Sunstone 아이콘으로 부릅니다.
4. 시나리오 돌려 보기: 부르는 카드 아래 **시연**을 누르고 시나리오를 고릅니다.
   - ① 출장 가는 길(S1~S9) · ② 아이 등원길(S10~S18) · ③ 시트 맞추기(S19~S25)
   - 고르면 첫 장면이 시작됩니다. 운전자 대사는 녹음본(앱과 다른 목소리)으로 나오고, 앱이 Pleos TTS로 답합니다.
   - **다음 장면**: 왼쪽 위 마이크 칸을 0.8초 **길게 누릅니다**. 카드가 떠 있어 마이크 칸이 안 보이면 카드의 Sunstone 아이콘을 길게 누릅니다.
     또는 마이크 칸(카드가 떠 있으면 카드의 Sunstone 아이콘)을 짧게 눌러 부르는 카드를 열고 **[다음 장면]**을 누릅니다.
   - 장면을 준비하는 몇 초 동안은 넘어가지 않습니다([다음 장면]이 "준비 중"으로 보입니다).
   - 다른 시나리오로 가려면 부르는 카드의 **[처음]** → **시연**.
   - 대사 대신 카드의 버튼을 직접 눌러도 됩니다.

시연 모드는 에뮬레이터의 Virtual Console(에뮬레이터 안 127.0.0.1:8088)로 기어·속도·문·탑승을 넣습니다. 프로토타입이라 차 상황을 스스로 만들 수 없어 제출 APK에도 넣었습니다.

## 테스트 환경과 필요 설정

소스에서 빌드할 때만 필요합니다.

- JDK 17~21 (`JAVA_HOME`). Android Studio 기본 JDK가 25면 Gradle 8.10.2가 시작하지 못합니다.
- `local.properties`(저장소에 올리지 않음)에 넣을 값

  | 키 | 값 | 필요한 때 |
  |---|---|---|
  | `sdk.dir` | Android SDK 경로 | 항상 (Android Studio가 넣어 줍니다) |
  | `release.storeFile` | release keystore 파일 경로 | `assembleRelease` |
  | `release.storePassword` | keystore 비밀번호 | `assembleRelease` |
  | `release.keyAlias` | 키 이름 | `assembleRelease` |
  | `release.keyPassword` | 키 비밀번호 | `assembleRelease` |

  | `sunstone.kakao.restKey` | 카카오 로컬 REST 키 | 시연 밖 실제 검색(목적지, 근처 주유소) |
  | `sunstone.opinet.key` | 오피넷 무료 API 키 | 시연 밖 실제 주유소 가격·세차 시설 여부 |
  | `sunstone.fuel.code` | 유종(휘발유 `B027`, 경유 `D047`) | 오피넷 가격. 없으면 휘발유 |

  release 값이 없으면 `assembleRelease`가 무엇이 없는지 말하고 멈춥니다. debug 빌드에는 필요 없습니다.
  바깥 API 키는 APK에 넣지 않습니다. debug 앱을 한 번 실행한 뒤 `tools/configure-api.ps1`이 기기 안 설정 파일로 보냅니다
  ([local.properties.example](local.properties.example)). **시연 모드는 키와 네트워크 없이 준비 값으로 돕니다.** 날씨(Open-Meteo)는 키가 없습니다.

```
./gradlew :routines:test         # 기능 테스트 (차 없이 대사집 전체)
./gradlew :app:installDebug      # debug 설치 (adb 데모 명령 포함)
./gradlew :app:assembleRelease   # 서명한 APK → apk/westone_Sunstone_release.apk
```

debug 빌드에는 adb 데모 명령이 더 있습니다.
```
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "scene S3"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "say 세차할게"
```
터미널에서 말과 장면을 넣고 대답을 보려면 `tools/talk.ps1`, 에뮬레이터 GPS·속도·기어를 움직이려면 `tools/scene-move.ps1 -Scenario 1`.
`weather rain`(내일 비)·`rain4`·`dry`·`unknown`·`live`로 시연 밖 날씨를 바꿔 [docs/SPEC.md](docs/SPEC.md) 4장의 갈래를 볼 수 있습니다.
앱이 하는 말은 Pleos TTS(ON_DEVICE)로 읽습니다. TTS 준비가 늦거나 실패하면 대사집 녹음본으로 대신합니다(대사를 고치면 `tools/make-voice.ps1`을 다시 돌립니다).
운전자 말은 듣는 중 줄이 떠 있는 동안 Pleos STT(ON_DEVICE)로 듣습니다. 에뮬레이터는 `-allow-host-audio`로 켜야 마이크 소리가 들어옵니다.

## 왜 만들었나

세차 모드, 차일드락, 시트 메모리처럼 현대차에는 잘 만든 기능이 많습니다. 그런데 설정 메뉴 깊숙이 있거나,
켜는 조건을 몰라서, 혹은 여러 개를 하나씩 눌러야 해서 거의 쓰이지 않습니다.
Sunstone은 그 기능들을 필요한 순간에 한 번에 세팅하고, 끝나면 원래대로 돌려놓습니다.

## 어떻게 동작하나

- **부르면 왼쪽에** — 마이크 칸이나 하단 막대 아이콘으로 부르면 왼쪽 구역에 루틴 세 줄이 뜹니다. 말하거나 눌러서 켭니다. 오른쪽 내비는 그대로입니다.
- **말 한마디로** — "세차할게" 하면 창문 닫기, 와이퍼 끄기, 내기 순환, 문 잠금을 한 번에 맞춥니다.
- **상황을 보고 먼저, 하나만** — 길안내를 시작할 때 연료가 낮으면 주유를, 세차한 지 오래됐고 곧 비가 오지 않으면 세차도 한 번에 묻습니다. 아이 시설로 갈 때는 차일드락을 권합니다. "네"를 받은 뒤 실행하고, 거절하면 그날은 다시 권하지 않습니다.
- **끝나면 원래대로** — 바꾸기 전 값을 기억해 두었다가 그대로 돌려놓습니다. 창문은 다시 열지 않고 문 잠금은 풀지 않습니다.
- **못 하는 건 알려 주기** — 앱이 직접 할 수 없는 단계(사이드미러 접기, 뒷문 차일드락)는 카드에 "직접"으로 보이고 짧게 말로 안내합니다.
- **말은 짧게, 자세한 건 카드에** — 카드는 뜨는 동안만 왼쪽 구역을 쓰고, 볼일이 끝나면 사라져 주행 화면이 돌아옵니다. 듣는 중·들은 말·결과 알림은 카드 맨 아래 한 줄에 보입니다.
  화면 규격은 [docs/SCREEN.md](docs/SCREEN.md).

| 루틴 | 하는 일 | 시연 |
|---|---|---|
| 주유와 세차 권하기 | 목적지를 말하면 길안내를 시작합니다. 연료가 25% 이하이거나 주행 가능 거리가 모자라면 주유소를 찾아 **한 번만 묻습니다.** 세차한 지 14일 이상이고 3일 안에 비 예보가 없으면 "주유하고 세차할까요?"(근거 칸: 연료 · 세차일 · 날씨), 아니면 "주유할까요?"만 묻고 비 때문이면 "내일 비 예보"를 보입니다. 운전자가 먼저 "세차도 하자"고 하면 비 소식을 한 번 알립니다 | 시나리오 ① S1~S4, 다른 갈래는 단위 테스트 |
| 세차 모드 | 창문 닫기, 와이퍼 끄기, 내기 순환, 문 잠금. 사이드미러는 "직접". 세차를 예약한 주유소에서는 세차 입구에 서면 묻지 않고 5초 뒤 켭니다(말이나 버튼으로 취소). D로 바꾸면 원래대로 돌리고 미러 확인을 말합니다. 20분 넘게 켜져 있으면 "세차 끝났나요?"를 한 번 묻습니다 | 시나리오 ① S5~S9 |
| 차일드락 | 유치원으로 길안내하면 "아이 타고 있나요?"로 권하고, "응, 잠가 줘"면 뒷창 잠금·문 잠금·공조를 맞춥니다. 뒷문 차일드락은 "직접". 주행 중 뒷자리 벨트가 풀리면 알리고, 도착해 P면 뒷창 잠금을 풉니다. 집으로 갈 때 "아침처럼" 다시 권합니다 | 시나리오 ② S10~S18 |
| 시트 메모리 | "시트 좀 맞춰 줘"로 시작해 "앞으로 조금"·"등받이 세워 줘"처럼 말로 조금씩 맞추고, "됐어"면 이름을 물어 그 이름으로 기억합니다. "옆에 희정이 앉았어"면 조수석을 저장된 자세로 맞춥니다 | 시나리오 ③ S19~S25 |

## 되는 것과 조작한 것

[docs/api-spike.md](docs/api-spike.md)의 실측과 같은 말로 적습니다.

| 구분 | 내용 |
|---|---|
| 실제 | 내비 경로 시작·경유지 넣기(NaviHelper), 창문·와이퍼·내기 순환·문 잠금·공조 제어와 되돌리기(Vehicle SDK), 기어·문·뒷좌석 탑승 읽기, 시트 축 움직이기, 왼쪽 구역 카드, 앱이 하는 말(Pleos TextToSpeech ON_DEVICE) |
| 실제 (시연 밖, 키 필요) | 목적지 후보(카카오 로컬), 근처 주유소 가격과 세차 시설 여부(오피넷, 안 되면 카카오 주유소 검색), 7일 비 예보(Open-Meteo), 운전자 말 듣기(Pleos SpeechToText ON_DEVICE) |
| 조작 (시연 모드가 넣는 값) | 기어·속도·문·탑승(Virtual Console), 연료 %·마지막 세차일·좌석 벨트·장소(주유소·유치원·집 도착), 주유소 세 곳과 날씨(7일 동안 비 없음) |
| 조작 (녹음본) | 시연 모드의 운전자 말. 장면마다 같은 대사가 나오도록 녹음본을 내고 그 말을 들은 말로 넣습니다 |
| 조작 (대신하는 것) | 도착 알림(내비 도착 콜백이 오지 않아 위치 근접 알림), 뒷문 차일드락(SDK에 없어 안내 문장) |
| 추정 | 시트 위치. 앱이 읽을 수 없어 움직인 시간으로 추정합니다 |
| 예시 값 | **시연 모드의 주유소 가격과 세차 가능 여부, 날씨는 예시 값입니다.** 주유소 이름·좌표는 실제 상호지만 "세차도 되는 곳이에요"는 지어낸 예시입니다. "OO유치원"·집은 지도에서 고른 지점입니다 |
| 허가 | 왼쪽 구역에 그리기(다른 앱 위에 그리기)는 adb로 허가합니다. 실제 차에서 일반 앱이 받을 수 있는지는 미확인입니다 |
| 버튼만 | 부르는 카드의 [+ 루틴 추가]는 "준비 중"만 알립니다 |

## Pleos Connect SDK 활용

| SDK | 쓰는 값 · 기능 | Sunstone에서 하는 일 |
|---|---|---|
| Vehicle (Gear, Door, Seat) | 기어, 문 열림, 좌석 탑승 | 정차·주차 판단, 뒷좌석 탑승 확인 |
| Vehicle (Window, Wiper, HVAC, Door) | 창문 위치·잠금, 와이퍼, 내기 순환, 공조 전원, 문 잠금 | 세차 모드·차일드락 실행과 되돌리기 |
| Vehicle (Seat) | 시트 축 이동(운전석·조수석) | 말로 조금씩 맞추기, 사람별 자세 맞추기 (위치를 읽을 수 없어 움직인 시간으로 맞춤) |
| Vehicle (Odometer, EvBattery) | 주행 가능 거리, 배터리 | 읽기만 함(시나리오는 주유 기준이라 연료 %를 시연 값으로 넣음) |
| AAOS 차량 속성 | 속도, 연료 종류 | 주행 중 판단 |
| NaviHelper | 경로, 경유지, 남은 거리·통행료 | 말로 시작하는 길안내, 주유소 경유, 통행료로 고속도로 가늠 |
| FusedLocation | 근접 알림(들어감·나감), 현재 위치 | 주유소·유치원 도착 감지, 길안내 목적지·경유지 도착, 근처 주유소·날씨를 찾을 자리 |
| TextToSpeech | ON_DEVICE 합성(`speak`) | 앱이 하는 말. 안 되면 녹음본 |
| SpeechToText | ON_DEVICE 인식(`request`, 최종 결과) | 듣는 중 줄이 떠 있는 동안 운전자 말 듣기. 안 되면 카드 버튼 |

API별 실측 결과와 시연에서 실제 값·조작 값을 어떻게 쓰는지는 [docs/api-spike.md](docs/api-spike.md)에 있습니다.

## 구조

```
routines/   순수 Kotlin. 루틴, 실행 엔진, 말 알아듣기, 가짜 차. 차 없이 테스트로 끝까지 돈다
app/        Pleos SDK 연결, 음성, 왼쪽 구역 카드, 시연 모드
```
자세한 내용은 [docs/STRUCTURE.md](docs/STRUCTURE.md).
화면 규격과 시나리오 장면별 상세는 [docs/SCREEN.md](docs/SCREEN.md)에 있습니다.

## 팀

박건준(앱 연결) · 박희정(기능 구현) — westone
