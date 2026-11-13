<#
작성: 2026-11-11 17:40:00 · 박희정
수정: 2026-11-13 10:00:00 · 박희정
목적: 대사집 문장을 미리 녹음한 음성 파일(WAV)로 만든다. 에뮬레이터에는 쓸 수 있는
      TTS 엔진이 없어서(초기화 -1) 시연 영상의 소리를 이 녹음본으로 낸다.
      1. routines ScriptBook.kt에서 장면마다 `expectSaid`(앱이 해야 하는 말)를 읽는다
      2. 대사집 밖에서 자주 하는 말(-Extra)을 더한다
      3. Windows 한국어 음성(Microsoft Heami)으로 읽어 app/src/main/assets/voice/<md5>.wav로
         저장한다. 파일 이름은 문장 UTF-8의 MD5(소문자 16진수)이고 앱 `AndroidSpeaker`가
         같은 방식으로 찾는다
      4. 운전자 대사(`Utterance` 대표 문장)는 앱과 다른 목소리로 들리게 낮고 느리게
         바꿔 app/src/main/assets/driver/<md5>.wav로 저장한다. 시연 `DriverVoice`가 낸다
      대사집을 고치면 다시 돌린다. 이미 있는 파일은 지우고 새로 만든다.
인자: -Extra  대사집 밖에서 녹음할 문장들
근거: 인용 — Tab 1 5.2 음성 출력 "기본 TTS → 대사집 문장은 미리 녹음한 파일".
      측정 — Pleos 에뮬레이터 v2.1.0 TTS 엔진은 ai.pleos.playground.caas 하나이고
      앱 TextToSpeech 초기화가 -1로 실패했다(2026-11-11)
미구현: 프로토타입이라 `expectSaid = listOf(...)` 안을 글자 그대로 읽는다. 쉼표로
      나뉜 것은 따로 말하는 문장, `+`로 이은 것은 한 문장으로 본다(시나리오 ② S17은
      두 문장).
      PC에 한국어 음성이 Heami 하나뿐이라, 운전자 목소리는 같은 음성의 WAV 머리에
      적힌 표본율을 낮춰(DRIVER_RATE) 낮고 느리게 들리게 만든다
#>
param(
    # 대사집 밖에서 시연 중 나올 수 있는 말. 앱 문장 틀(WashLines·TripLines·CardLines·strings.xml)과
    # 글자가 같아야 녹음본을 찾는다. 세차 모드 질문 답은 KnowledgeTest의 기대 문장과 같다
    [string[]]$Extra = @(
        "알겠어요.", "잘 못 알아들었어요.", "되돌릴 게 없어요.",
        "차가 멈춘 뒤에 할게요.", "P나 N에 두시면 시작할게요.", "문을 모두 닫으시면 시작할게요.",
        ("세차 모드는 세차 전에 미러·창문·와이퍼·공조를 한 번에 준비하는 현대차 기능이에요. " +
            "창문·와이퍼·내기 순환·문 잠금은 제가 맞춰 드리고, 사이드미러 접기와 N 유지는 말로 안내해 드려요."),
        "그 기능은 아직 잘 몰라요.", "차일드락 끝낼게요. 뒷창문 잠금 풀었어요."
    )
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Speech
$root = Split-Path $PSScriptRoot -Parent                                                  # 저장소 폴더
$book = Join-Path $root "routines\src\main\kotlin\com\westone\sunstone\routines\script\ScriptBook.kt"  # 대사집
$out = Join-Path $root "app\src\main\assets\voice"                                         # 녹음본 폴더
$driverOut = Join-Path $root "app\src\main\assets\driver"                                 # 운전자 녹음본 폴더
$DRIVER_RATE = 0.84                                                                       # 운전자 표본율 배율(낮고 느리게)

<#
함수: Get-Lines
목적: 대사집에서 장면마다 앱이 해야 하는 말을 꺼낸다.
#>
function Get-Lines {
    $text = Get-Content $book -Raw -Encoding UTF8
    $blocks = [regex]::Matches($text, 'expectSaid\s*=\s*listOf\((.*?)\)\s*,', 'Singleline')   # 장면마다 기대 말 묶음
    foreach ($b in $blocks) {
        $body = $b.Groups[1].Value                                         # listOf( ) 안
        $lits = [regex]::Matches($body, '"((?:[^"\\]|\\.)*)"')             # 글자 조각들
        $line = ""                                                          # 지금 잇는 문장
        for ($i = 0; $i -lt $lits.Count; $i++) {
            $line += $lits[$i].Groups[1].Value
            $end = $lits[$i].Index + $lits[$i].Length                       # 조각 끝 위치
            $next = if ($i + 1 -lt $lits.Count) { $lits[$i + 1].Index } else { $body.Length }  # 다음 조각 위치
            if ($body.Substring($end, $next - $end) -notmatch '\+') { $line; $line = "" }  # +가 없으면 문장 끝
        }
    }
}

<#
작성: 2026-11-13 03:55:00 · 박건준
함수: Get-DriverLines
목적: 대사집에서 장면마다 운전자 대사 대표 문장(`Utterance(text = ...)`)을 꺼낸다.
#>
function Get-DriverLines {
    $text = Get-Content $book -Raw -Encoding UTF8
    [regex]::Matches($text, 'Utterance\(\s*text\s*=\s*"((?:[^"\\]|\\.)*)"', 'Singleline') | ForEach-Object { $_.Groups[1].Value }
}

<#
작성: 2026-11-13 03:55:00 · 박건준
함수: Set-Lower
목적: WAV 머리의 표본율과 초당 바이트 수를 배율만큼 낮춰 같은 소리가 낮고 느리게
      나오게 한다. 머리는 RIFF 표준 그대로(표본율 24번째, 초당 바이트 28번째 바이트)다.
#>
function Set-Lower([string]$file, [double]$ratio) {
    $bytes = [IO.File]::ReadAllBytes($file)
    $rate = [BitConverter]::ToInt32($bytes, 24)                                          # 원래 표본율
    $block = [BitConverter]::ToInt16($bytes, 32)                                         # 표본 하나의 바이트 수
    $new = [int]($rate * $ratio)                                                          # 바꿀 표본율
    [BitConverter]::GetBytes($new).CopyTo($bytes, 24)
    [BitConverter]::GetBytes($new * $block).CopyTo($bytes, 28)
    [IO.File]::WriteAllBytes($file, $bytes)
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
Write-Host ("녹음본 {0}개 → {1}" -f $lines.Count, $out)

New-Item -ItemType Directory -Force $driverOut | Out-Null
Get-ChildItem $driverOut -Filter *.wav | Remove-Item
$drivers = @(Get-DriverLines) | Select-Object -Unique                                     # 운전자 대사들
foreach ($line in $drivers) {
    $file = Join-Path $driverOut ((Get-Md5 $line) + ".wav")                               # 저장할 파일
    $voice.SetOutputToWaveFile($file)
    $voice.Speak($line)
    $voice.SetOutputToNull()
    Set-Lower $file $DRIVER_RATE
    Write-Host ("{0}  {1}" -f (Split-Path $file -Leaf), $line)
}
$voice.Dispose()
Write-Host ("운전자 녹음본 {0}개 → {1}" -f $drivers.Count, $driverOut)
