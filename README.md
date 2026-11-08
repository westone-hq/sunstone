# Sunstone

현대차에 이미 있지만 안 쓰이는 기능을, 상황을 알아채거나 말 한마디로 한 번에 실행하고 끝나면
원래대로 돌리는 Pleos Connect 앱. 2026 현대자동차그룹 차량용 앱 개발 공모전 출품작.

1차: 세차 모드, 차일드락, 시트 메모리, 길안내 대화 흐름(주유 제안 → 세차 추천 → 도착 시 세차 모드).
2차: 하이패스 카드 확인, 뒷유리 블라인드. 여유 있으면 공기청정, 발레.

## 구조

| 영역 | 모듈 | 담당 |
|---|---|---|
| 기능 구현 | `routines/` 순수 Kotlin. 루틴, 실행 엔진, 말 알아듣기, 가짜 차 | 박희정 |
| 앱 연결 | `app/` Pleos SDK, 내비, 바깥 API, 음성, 화면, 데모 입력 | 박건준 (대표) |

자세한 폴더·창구·용어는 [docs/STRUCTURE.md](docs/STRUCTURE.md).

## 문서

| 문서 | 내용 |
|---|---|
| [docs/STRUCTURE.md](docs/STRUCTURE.md) | 구조, 담당, 창구, 용어 |
| [docs/COMMENT_RULES.md](docs/COMMENT_RULES.md) | 주석·코드·커밋 규칙 |
| [docs/HANDOFF_FEATURES.md](docs/HANDOFF_FEATURES.md) | 기능 구현 시작 안내, 참고 자료 |
| [docs/api-spike.md](docs/api-spike.md) | Pleos API 실측 기록 |
| [CLAUDE.md](CLAUDE.md) | Claude Code 작업 안내 |

## 열기와 실행

1. Android Studio에서 `sunstone` 폴더를 연다. Gradle 동기화를 기다린다.
2. 에뮬레이터 `Pleos Connect v2.1.0`(API 34)을 켜고 `app`을 실행한다.
3. 차 값은 Virtual Console로 바꾼다: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088

```
./gradlew :routines:test                 # 기능 테스트
./gradlew :app:installDebug              # 앱 설치
```

## 데모 명령 (debug 빌드)

```
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "place carwash"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "say 세차할게"
adb shell am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "speed 30"
```
전체 명령은 `app/src/debug/.../DemoReceiver.kt`. `fake on` 후 앱을 다시 켜면 가짜 차로 돈다.

Gleo와 같은 인텐트:
```
adb shell am start -a RUN_ROUTINE -n com.westone.sunstone/.ui.MainActivity --es ACTION_PARAM '{"routine":"WASH"}'
```

## API 키

`local.properties`에만 둔다. 저장소에 올리지 않는다. 키가 없으면 바깥 API는 고정 응답으로 돈다.
