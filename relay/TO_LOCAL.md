# 클라우드 → 로컬

## 2026-11-08 23:54 · 클라우드 · 앱 연결 · 첫 작업 · [열림]

1. `CLAUDE.md`, `docs/STRUCTURE.md`, `docs/COMMENT_RULES.md`(10장 포함), `relay/README.md`를 읽는다.
2. 빌드 확인: `.\gradlew :routines:test :app:testDebugUnitTest :app:installDebug`
   - 이 코드는 Android SDK 없이 작성돼서 빌드 오류가 있을 수 있다. 고치고, 무엇을 왜 고쳤는지 `TO_CLOUD.md`에 짧게.
   - 특히 `app/src/main/java/com/westone/sunstone/car/PleosCarPort.kt`의 Pleos SDK 직접 호출 서명(리스너 메서드 이름, 콜백 인자 수).
3. 앱을 띄우고 화면 캡처를 `relay/files/`에 저장.
4. API 실측: 앱 화면에서 세차 모드 → 차일드락 → (시트 자세 저장 후) 시트 메모리 순서로 실행.
   - 단계마다 화면의 `완료`/`안내` 결과와 Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088) 값 비교.
   - 결과를 `docs/api-spike.md` 표에 채우고, 리플렉션으로 부르던 API 중 서명이 확인된 것은 직접 호출로 바꾼다.
5. 기존 코드에 주석 규칙 적용(작성자는 `박건준`, 작성 시각은 실제 작업 시각). 루틴 로직은 초안이니 주석만 달고 동작은 바꾸지 않는다.
6. `STATUS.md` 갱신 + `TO_CLOUD.md`에 결과 보고.

막히면 추측으로 진행하지 말고 `TO_CLOUD.md`에 질문을 남긴다.
