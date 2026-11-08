# Sunstone — Claude Code 작업 안내

현대차 Pleos Connect 앱 공모전 출품작. 현대차에 있지만 안 쓰이는 기능을, 상황을 알아채거나 말 한마디로 한 번에 실행하고 끝나면 되돌린다.
사용자와는 한국어로, 짧고 직접적으로 이야기한다.

## 목표 (일정보다 이게 기준)
시연 대사집(아래 링크 4장, S0~S13)이 처음부터 끝까지 끊김 없이 돌고 영상으로 남길 수 있으면 완성.
- 실제 경로: Pleos 에뮬레이터 + Virtual Console에서 차 값이 실제로 바뀜
- 안전 경로: 가짜 차(FakeCar) + 고정 응답. 네트워크·마이크·API 키 없어도 같은 대화

범위: 1차 = 세차 모드, 차일드락, 시트 메모리, 대화 흐름(길안내→주유 제안→세차 추천→도착 시 세차 모드).
2차 먼저 = 하이패스 카드 확인(안내), 뒷유리 블라인드(후진 시 내림). 여유 있으면 공기청정, 발레(안내).

## 담당과 승인
- 박건준(대표): ② 앱 연결(`app/`) + 시연 빌드·영상 + 최종 결정.
- 박희정: ① 기능 구현(`routines/`). 시작 안내는 `docs/HANDOFF_FEATURES.md`.
- 지금 있는 루틴·문장·판단 숫자는 **초안(미승인)**. 기능은 "설명 → 대표 승인 → 테스트 → 구현" 순서로만
  만든다. 승인 전에는 기능 코드를 늘리거나 다듬지 않는다.
- 창구(`routines/.../port/`)를 바꾸려면 relay로 제안하고 대표가 정한다.

## 함께 일하는 방식
- 클라우드 Claude(설계·문서)와 로컬 Claude Code(빌드·실행·코드)가 `relay/` 폴더로 주고받는다. 규칙은 `relay/README.md`.
- 작업을 시작할 때 `relay/TO_LOCAL.md`의 `[열림]` 글부터 처리한다. 끝나면 `relay/STATUS.md`와 `relay/TO_CLOUD.md`를 쓴다.
- 결정이 필요한 것(범위, 비용, 키, 시연 문장)은 추측하지 말고 `TO_CLOUD.md`나 사용자에게 묻는다.
- **주석·코드 규칙: `docs/COMMENT_RULES.md`** — 작업 전마다 다시 읽는다. 작성자는 그 작업을 맡은 사람 이름(`박건준` 또는 `박희정`).
  특히 10장(사람이 쓴 코드처럼): 이모지 금지, 코드 읽어 주는 주석 금지, 뜻 없는 이름 금지, 커밋 형식.
- 커밋·PR에 `Co-Authored-By`, `Generated with` 같은 도구 표기를 절대 넣지 않는다.
- 저장소: https://github.com/westone-hq/sunstone (비공개). `local.properties`·키는 절대 커밋하지 않는다.

## 설계 문서 (먼저 읽기)
- `docs/STRUCTURE.md` (구조·창구·용어), `docs/COMMENT_RULES.md`, `docs/HANDOFF_FEATURES.md`, `docs/api-spike.md`
- 개발 정리(가장 중요): https://claude.ai/artifact/QJaAm8ipQEGxsrghWDeyLF
- 기능 설계서: https://claude.ai/artifact/7gW1ehCNaFRzc6Y3jmvkFL

## 구조: 두 영역
- `routines/` ① 기능 구현. 순수 Kotlin/JVM, 안드로이드·Pleos 의존 금지. 루틴·실행 엔진·말 알아듣기(`understand/`)·가짜 차·테스트.
- `app/` ② 앱 연결. Pleos SDK(`car/PleosCarPort.kt`), 음성·Gleo·화면·데모 입력, 바깥 API.
- 모든 입력(화면·말·Gleo·adb)은 `AppGraph.handle(VoiceAction)` 한 곳으로.
- 바깥 API(카카오·오피넷·기상청·에어코리아)는 app 쪽에만, 키 없으면 고정 응답으로 자동 전환.

## 지켜야 할 규칙
- 제어는 "원래 값 저장 → 실행 → 다시 읽어 확인". 실패/미지원이면 그 단계만 음성 안내로 바꾸고 루틴은 계속.
- 되돌리기는 기본값이 아니라 저장한 원래 값으로. 창문은 다시 열지 않고 문 잠금은 풀지 않는다.
- 시트는 P단에서만, 기어 바뀌면 즉시 STOP. 차를 바꾸는 일은 정차 중에만.
- LLM을 붙이더라도 의도·문장 다듬기만. 실행 결정은 규칙 엔진이 한다.
- `PleosCarPort`의 리플렉션 호출(서명 미확정 API)은 실측으로 확인되면 직접 호출로 바꾸고 `docs/api-spike.md`에 결과를 적는다.

## 명령 (Windows PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"; $adb = "$sdk\platform-tools\adb.exe"

.\gradlew :routines:test                      # 기능 구현 테스트
.\gradlew :app:testDebugUnitTest :app:installDebug
& $adb shell am start -n com.westone.sunstone/.ui.MainActivity
& $adb logcat -s SunstoneCar SunstoneVoice SunstoneDemo Sunstone
& $adb exec-out screencap -p > screen.png     # 에뮬레이터 화면 확인
& $adb forward tcp:8088 tcp:8088              # Virtual Console → http://127.0.0.1:8088

# 데모 장면 주입 (debug 빌드)
& $adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "place carwash"
& $adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "say 세차할게"
```
에뮬레이터: AVD "Pleos Connect v2.1.0" (시스템 이미지 Pleos Connect v2.1.0 APIs, API 34).

## 지금 할 일
앱 연결(박건준):
1. `:routines:test` 통과 확인, `:app:installDebug` 빌드 오류 고치기(Android SDK 없이 작성된 코드라 첫 빌드 오류가 있을 수 있음).
2. Pleos API 실측: `tools/api-explorer`와 앱으로 값 바꾸기 → Virtual Console 비교 → `docs/api-spike.md` → 리플렉션 호출 확정.
3. 내비 SDK(NaviHelper 3.0.5) 실측: 경로·경유지·도착 알림.
4. 시연 빌드/Pleos 빌드 2종 나누기.

기능 구현(박희정): `docs/HANDOFF_FEATURES.md` 6장 순서. 세차 모드 설명 → 승인부터.
