# Sunstone

현대차에 있지만 안 쓰이는 기능을, 상황을 알아채거나 말 한마디로 한 번에 실행하고 끝나면 되돌리는 Pleos Connect 앱.
사용자와는 한국어로, 짧고 직접적으로, 존댓말로 이야기한다.

## 먼저 읽기
- `docs/STRUCTURE.md` 구조·창구·용어
- `docs/COMMENT_RULES.md` 주석·코드·커밋 규칙. 코드를 쓰기 전마다 다시 읽는다
- `CONTRIBUTING.md` 담당, 기능 승인, 브랜치·PR
- `docs/api-spike.md` Pleos API 실측 결과
- `docs/SCREEN.md` 화면 1차 규격, 시나리오 ① 장면별 상세

## 담당
- 박건준: `app/` 앱 연결, 시연 빌드, 기능 승인과 최종 결정
- 박희정: `routines/` 기능 구현
- 지금 세션을 연 사람의 영역만 고친다. 다른 영역이 필요하면 멈추고 사용자에게 말한다.
- 주석 `작성:`·`수정:`의 이름은 이 세션을 연 사람 이름으로 적는다.

## 지켜야 할 것
- 프로토타입이다. 최대한 실제로 만들고, 조사해도 안 되는 부분만 시연 시나리오대로 값을 넣어 조작한다(콘솔 API, 데모 명령, 장면 준비).
- 조작하거나 우회한 코드는 주석 `미구현:`에 "프로토타입이라 …"로 이유를 적는다.
- 현대차·Pleos 문의는 할 수 없다. 막히면 SDK 다른 함수 → AAOS → 콘솔 API → 에뮬레이터 기능 → 다른 팀 저장소 → 문서 순서로 대안을 찾는다.
- 루틴·문장·판단 숫자는 승인 전 초안이다. 승인된 이슈 없이 기능 코드를 늘리거나 다듬지 않는다.
- `routines/`는 순수 Kotlin. 안드로이드·Pleos 의존을 넣지 않는다.
- 제어는 "원래 값 저장 → 실행 → 다시 읽어 확인". 실패하면 그 단계만 음성 안내로 바꾸고 루틴은 계속.
- 되돌리기는 저장한 원래 값으로. 창문은 다시 열지 않고 문 잠금은 풀지 않는다.
- 시트는 P단에서만. 차를 바꾸는 일은 정차 중에만.
- LLM을 붙여도 말 알아듣기·문장 다듬기만. 실행 결정은 규칙으로 한다.
- 데모·테스트 우회는 `app/src/debug/`와 `AppGraph` 조립부에만.
- 커밋·PR에 도구 표기 줄을 넣지 않는다(`.claude/settings.json`에서도 껐다).
- `local.properties`, 키, keystore는 커밋하지 않는다.
- 결정이 필요한 것(범위, 비용, 키, 시연 문장)은 추측하지 말고 사용자에게 묻는다.

## 명령 (Windows PowerShell)
```powershell
# JAVA_HOME: JDK 17~21. Android Studio 기본 jbr이 25면 Gradle 8.10.2가 시작하지 못한다
$sdk = "$env:LOCALAPPDATA\Android\Sdk"; $adb = "$sdk\platform-tools\adb.exe"

.\gradlew :routines:test
.\gradlew :app:assembleDebug
.\gradlew :app:installDebug
& $adb shell am start -n com.westone.sunstone/.ui.MainActivity
& $adb logcat -s SunstoneCar SunstoneVoice SunstoneDemo Sunstone
& $adb exec-out screencap -p > screen.png
& $adb forward tcp:8088 tcp:8088              # Virtual Console → http://127.0.0.1:8088

# 데모 장면 주입 (debug 빌드)
& $adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "place carwash"
& $adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "say 세차할게"
```
에뮬레이터: AVD "Pleos Connect v2.1.0" (시스템 이미지 Pleos Connect v2.1.0 APIs, API 34).
