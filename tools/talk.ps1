<#
작성: 2026-11-11 15:05:00 · 박희정
목적: 에뮬레이터의 Sunstone 앱에 말을 직접 쳐 넣고, 앱이 한 말을 바로 본다.
      음성 인식 없이 대사집 대답("응", "좋아", "괜찮아")을 시험하려고 쓴다.
      - 그냥 치면 말로 넣는다(데모 `say`)
      - /로 시작하면 장면을 바꾼다
        /place gas|carwash|kids|none  장소(데모 `place`)
        /gear p|r|n|d                 기어(Virtual Console)
        /speed 30                     속도 km/h(Virtual Console)
        /fuel 18                      연료 %(데모 `fuel`)
        /washed 36                    마지막 세차를 N일 전으로(데모 `washed`)
        /accept, /dismiss             화면의 네·아니요 버튼과 같다
        /ready1                       시나리오 ① 준비: P단, 연료 18%, 마지막 세차
                                      36일 전, 현재 위치 서울(강남역)
        /move1                        시나리오 ① 이동: 서울 → 경유지 주유소(약 95초)
        /leave1                       시나리오 ① 세차 후 출발: D·30km/h로 주유소를 떠남
        /데모명령 …                     그 밖의 데모 명령을 그대로 보낸다
      - q 를 치면 끝낸다
      켜져 있는 동안 Virtual Console 연결 신호를 1초마다 보낸다. 끊기면 내기
      순환·문 잠금 제어가 실패한다.
인자: 없음. 에뮬레이터와 Sunstone debug 앱이 켜져 있어야 한다
근거: 측정 — 콘솔 연결 신호가 끊긴 뒤 세차 모드의 내기 순환은 Failed, 문 잠금은
      3초 응답 없음. 신호를 보내는 동안에는 둘 다 Done(2026-11-11 routine/wash)
#>

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }   # Android SDK 위치
$adb = "$sdk\platform-tools\adb.exe"      # adb
$console = "http://127.0.0.1:8088"        # Virtual Console
$gears = @{ p = "Park"; r = "Reverse"; n = "Neutral"; d = "Drive" }   # 짧은 기어 이름 → 콘솔 이름
$seoul = @{ lat = "37.4979"; lng = "127.0276" }   # 시나리오 ① 출발점(강남역). scene-move.ps1과 같다

<#
함수: Send-Demo
목적: 앱의 데모 수신기에 명령 한 줄을 보낸다. 한글이 깨지지 않게 adb shell 안에서
      통째로 따옴표로 감싼다.
#>
function Send-Demo([string]$cmd) {
    $safe = $cmd.Replace("'", "")
    & $adb shell "am broadcast -a com.westone.sunstone.DEMO -p com.westone.sunstone --es cmd '$safe'" | Out-Null
}

<#
함수: Send-Console
목적: Virtual Console API를 부른다. 연결 신호를 먼저 보내 차량 쪽 연결을 살린다
      (시작 직후에는 신호 작업이 아직 돌기 전이라 바로 보내면 실패한다).
      콘솔이 없으면 알리고 넘어간다.
#>
function Send-Console([string]$path) {
    try {
        Invoke-RestMethod -Method Post -Uri "$console/browser/session?client=talk" | Out-Null
        Invoke-RestMethod -Method Put -Uri "$console$path" | Out-Null
    }
    catch { Write-Host "  (콘솔에 보내지 못함: $path)" -ForegroundColor DarkYellow }
}

<#
함수: Show-Replies
목적: 보낸 뒤 앱이 한 말을 기다렸다 보여 준다. 마지막 말 뒤 2초 동안 더 없으면
      끝낸다. 아무 말도 없으면 최대 50초 기다린다(에뮬레이터를 켠 직후 첫 경로는
      시작까지 25초 안팎 걸리고 앱은 45초까지 기다린다). 로그를 지우지 않고 보내기 직전 시각 뒤의 줄만 본다. 문제가 생기면
      logcat에 기록이 남아 있어야 원인을 볼 수 있어서다.
인자: since — 보내기 직전 에뮬레이터 시각("MM-dd HH:mm:ss.000")
#>
function Show-Replies([string]$since) {
    $shown = 0                                     # 이미 보여 준 줄 수
    $quietSince = Get-Date                         # 마지막으로 새 말이 온 시각
    $start = Get-Date                              # 기다리기 시작한 시각
    while ($true) {
        Start-Sleep -Milliseconds 500
        $lines = @(& $adb logcat -d -T $since -s SunstoneVoice:I | Where-Object { $_ -match "말: " })   # 그 뒤로 한 말
        for ($i = $shown; $i -lt $lines.Count; $i++) {
            Write-Host ("  Sunstone: " + ($lines[$i] -replace "^.*말: ", "")) -ForegroundColor Cyan
            $quietSince = Get-Date
        }
        $shown = $lines.Count
        $quiet = ((Get-Date) - $quietSince).TotalSeconds   # 새 말 없이 지난 초
        $waited = ((Get-Date) - $start).TotalSeconds       # 기다린 초
        if (($shown -gt 0 -and $quiet -ge 2) -or $waited -ge 50) { break }
    }
}

& $adb forward tcp:8088 tcp:8088 | Out-Null
$heartbeat = Start-Job -ArgumentList $console {
    param($c)
    while ($true) {
        try { Invoke-RestMethod -Method Post -Uri "$c/browser/session?client=talk" | Out-Null } catch {}
        Start-Sleep -Seconds 1
    }
}

Write-Host "말을 치면 앱에 넣어요. /place gas, /gear p 처럼 /로 장면을 바꿔요. q 는 끝." -ForegroundColor Gray
try {
    while ($true) {
        $text = (Read-Host "운전자").Trim()
        if ($text -eq "") { continue }
        if ($text -eq "q") { break }
        $since = (& $adb shell date "+%m-%d_%H:%M:%S.000").Trim().Replace("_", " ")   # 보내기 직전 에뮬레이터 시각
        if ($text.StartsWith("/")) {
            $word, $arg = $text.Substring(1).Split(" ", 2)
            switch ($word) {
                "gear" {
                    $g = $gears[[string]$arg]
                    if ($g) { Send-Console "/vehicle/gear?gear=$g" } else { Write-Host "  기어는 p r n d 중 하나" }
                }
                "speed" { Send-Console "/vehicle/controls?speed=$arg" }
                "ready1" {
                    Send-Console "/vehicle/controls?speed=0"
                    Send-Console "/vehicle/gear?gear=Park"
                    Send-Demo "fuel 18"
                    Send-Demo "washed 36"
                    & $adb emu geo fix $seoul.lng $seoul.lat | Out-Null
                    Write-Host "  준비: P단, 연료 18%, 마지막 세차 36일 전, 서울(강남역)" -ForegroundColor Gray
                }
                "move1" { & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "scene-move.ps1") -Scenario 1 }
                "leave1" {
                    Send-Console "/vehicle/gear?gear=Drive"
                    Send-Console "/vehicle/controls?speed=30"
                    & $adb emu geo fix $seoul.lng $seoul.lat | Out-Null
                }
                default { Send-Demo $text.Substring(1) }
            }
        } else {
            Send-Demo "say $text"
        }
        Show-Replies $since
    }
} finally {
    Stop-Job $heartbeat; Remove-Job $heartbeat -Force
}
