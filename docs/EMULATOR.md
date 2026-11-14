# Pleos 에뮬레이터 실행·테스트

최신 시나리오 ①은 버튼을 누른 뒤 직접 목적지를 입력한다. 오피넷은 사용하지 않는다.
[현재 흐름과 검증](SCENARIO_ONE.md)을 참고한다.

2026-11-14 이 PC에 공식 Pleos Connect 2.1.0(API 34, x86_64)을 설치했다.
가상 기기 이름은 `Sunstone_Pleos`다. SDK는 `%LOCALAPPDATA%/Android/Sdk`에 있다.
Android Emulator 안정 버전과 명령행 도구를 설치했고 AEHD 2.2 가속 사용 가능 여부를 확인했다.
기존 다른 가상 기기는 삭제하거나 변경하지 않았다.

## 실행

프로젝트의 **`Start Sunstone.cmd`를 더블클릭**한다.
이미 실행 중이면 같은 가상 기기를 재사용하고, 부팅 후 Sunstone 화면을 연다.
Android Studio의 Tools → Device Manager에서도 `Sunstone_Pleos`를 실행할 수 있다.

APK와 로컬 API 설정은 이미 기기에 설치했다. 키는 앱 비공개 파일에 있으며 문서·APK에 넣지 않았다.
앱을 제거하거나 가상 기기를 초기화하면 APK 설치와 `tools/configure-api.ps1` 적용을 다시 해야 한다.

## 확인한 결과

- 가상 기기 생성·부팅·ADB 연결 성공 (`emulator-5554`, Android 사용자 10).
- Sunstone APK 설치·실행 성공.
- Pleos ON_DEVICE TTS 초기화·대사 읽기 확인.
- 카카오 “강남역” 검색 결과와 주소·목적지 선택 버튼 표시 확인.
- 기존 Pleos 내비의 경로 시작 이벤트 확인. 실제 도로 주행 전체를 검증했다는 뜻은 아니다.
- API 설정 도구가 파일 저장 전에 성공으로 표시하던 문제 발견·수정.
  ADB 전송을 `shell -T`로 변경하고 저장 파일과 로컬 값의 일치 검사까지 추가했다.
- 오피넷은 기존과 같이 빈 응답 때문에 실제 가격 추천이 보류될 수 있다.
- 실제 주유소 도착·이동·세차 전체 시나리오를 모두 통과했다고 보고하지 않는다.

로그·스크린샷은 Git 제외 `build/emulator-setup`에 있다. `sunstone-device.log`는 키를 제거한 로그다.
이 기록 이후 사용자가 직접 테스트한 기기 상태가 현재 화면에 남아 있을 수 있다.

## 직접 테스트

1. 오른쪽 앱의 **말 한마디** 입력칸에 `강남역으로 안내해 줘`를 입력하고 **말하기**를 누른다.
2. 후보가 여러 개면 이름·주소를 보고 버튼을 누르거나 번호를 입력한다.
3. `전주 날씨 알려줘`, `여기 날씨 어때?`로 기존 날씨 질문도 확인한다.
4. 세차의 반복 시연은 고정 시연 데이터 모드를 명시적으로 선택한 뒤 **① 출장 가는 길**을 사용한다.

현재 STT는 연결하지 않았다. 마이크를 눌러 실제 음성으로 입력하는 기능으로 오해하지 않는다.
기본 모드는 실시간이며 외부 API 실패를 가짜 성공으로 바꾸지 않는다.
고정 시연 모드 선택은 프로젝트 터미널에서 다음 명령으로 한다.

```powershell
$adbForSunstone = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adbForSunstone shell am broadcast --user current -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd "weather dry"
```

다시 실제 API를 사용하려면 같은 명령의 `weather dry`를 `weather live`로 바꾼다.
고정 시연에서는 가상 주유소 정보·예보임을 화면·대사에서 표시한다.

첫 부팅은 cold boot로 진행한다. 가상 기기 RAM은 4GB, 데이터 파티션은 6GB로 설정했다.
공식 시스템 이미지 자체는 압축 해제 후 약 18GB이며 다운로드 파일은 `build/emulator-setup`에 보관했다.

[공식 설치 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
