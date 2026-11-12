<#
작성: 2026-11-11 17:40:00 · 박희정
목적: 대사집 문장을 미리 녹음한 음성 파일(WAV)로 만든다. 에뮬레이터에는 쓸 수 있는
      TTS 엔진이 없어서(초기화 -1) 시연 영상의 소리를 이 녹음본으로 낸다.
      1. routines ScriptBook.kt에서 장면마다 `expectSaid`(앱이 해야 하는 말)를 읽는다
      2. 대사집 밖에서 자주 하는 말(-Extra)을 더한다
      3. Windows 한국어 음성(Microsoft Heami)으로 읽어 app/src/main/assets/voice/<md5>.wav로
         저장한다. 파일 이름은 문장 UTF-8의 MD5(소문자 16진수)이고 앱 `AndroidSpeaker`가
         같은 방식으로 찾는다
      대사집을 고치면 다시 돌린다. 이미 있는 파일은 지우고 새로 만든다.
인자: -Extra  대사집 밖에서 녹음할 문장들
근거: 인용 — Tab 1 5.2 음성 출력 "기본 TTS → 대사집 문장은 미리 녹음한 파일".
      측정 — Pleos 에뮬레이터 v2.1.0 TTS 엔진은 ai.pleos.playground.caas 하나이고
      앱 TextToSpeech 초기화가 -1로 실패했다(2026-11-11)
미구현: 프로토타입이라 장면마다 앱 말이 한 문장(문자열 이어 붙이기 포함)이라고 보고
      `expectSaid = listOf(...)` 안의 글자를 모두 이어 한 문장으로 만든다
#>
param(
    # 대사집 밖에서 시연 중 나올 수 있는 말. 앱 문장 틀(WashLines·TripLines·CardLines·strings.xml)과
    # 글자가 같아야 녹음본을 찾는다. 세차 모드 질문 답은 KnowledgeTest의 기대 문장과 같다
    [string[]]$Extra = @(
        "알겠어요.", "잘 못 알아들었어요.", "되돌릴 게 없어요.",
        "차가 멈춘 뒤에 할게요.", "P나 N에 두시면 시작할게요.", "문을 모두 닫으시면 시작할게요.",
        ("세차 모드는 세차 전에 미러·창문·와이퍼·공조를 한 번에 준비하는 현대차 기능이에요. " +
            "창문·와이퍼·내기 순환·문 잠금은 제가 맞춰 드리고, 사이드미러 접기와 N 유지는 말로 안내해 드려요."),
        "그 기능은 아직 잘 몰라요."
    )
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Speech
$root = Split-Path $PSScriptRoot -Parent                                                  # 저장소 폴더
$book = Join-Path $root "routines\src\main\kotlin\com\westone\sunstone\routines\script\ScriptBook.kt"  # 대사집
$out = Join-Path $root "app\src\main\assets\voice"                                         # 녹음본 폴더

<#
함수: Get-Lines
목적: 대사집에서 장면마다 앱이 해야 하는 말을 꺼낸다.
#>
function Get-Lines {
    $text = Get-Content $book -Raw -Encoding UTF8
    $blocks = [regex]::Matches($text, 'expectSaid\s*=\s*listOf\((.*?)\)\s*,', 'Singleline')   # 장면마다 기대 말 묶음
    foreach ($b in $blocks) {
        $parts = [regex]::Matches($b.Groups[1].Value, '"((?:[^"\\]|\\.)*)"') | ForEach-Object { $_.Groups[1].Value }
        ($parts -join "")
    }
}

<#
함수: Get-Md5
목적: 문장 UTF-8의 MD5를 소문자 16진수로 만든다(파일 이름).
#>
function Get-Md5([string]$s) {
    $md5 = [Security.Cryptography.MD5]::Create()
    $hash = $md5.ComputeHash([Text.Encoding]::UTF8.GetBytes($s))
    return -join ($hash | ForEach-Object { $_.ToString("x2") })
}

New-Item -ItemType Directory -Force $out | Out-Null
Get-ChildItem $out -Filter *.wav | Remove-Item
$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer                             # 음성 합성기
$voice.SelectVoice("Microsoft Heami Desktop")
$lines = @(Get-Lines) + $Extra | Select-Object -Unique                                    # 녹음할 문장들
foreach ($line in $lines) {
    $file = Join-Path $out ((Get-Md5 $line) + ".wav")                                     # 저장할 파일
    $voice.SetOutputToWaveFile($file)
    $voice.Speak($line)
    $voice.SetOutputToNull()
    Write-Host ("{0}  {1}" -f (Split-Path $file -Leaf), $line)
}
$voice.Dispose()
Write-Host ("녹음본 {0}개 → {1}" -f $lines.Count, $out)
