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
        /accept, /dismiss             화면의 네·아니요 버튼과 같다
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
목적: Virtual Console API를 부른다. 콘솔이 없으면 알리고 넘어간다.
#>
function Send-Console([string]$path) {
    try { Invoke-RestMethod -Method Put -Uri "$console$path" | Out-Null }
    catch { Write-Host "  (콘솔에 보내지 못함: $path)" -ForegroundColor DarkYellow }
}

<#
함수: Show-Replies
목적: 보낸 뒤 앱이 한 말을 기다렸다 보여 준다. 마지막 말 뒤 2초 동안 더 없으면
      끝낸다. 아무 말도 없으면 최대 15초 기다린다(세차 모드 실행에 10초 안팎 걸린다).
#>
function Show-Replies {
    $shown = 0                                     # 이미 보여 준 줄 수
    $quietSince = Get-Date                         # 마지막으로 새 말이 온 시각
    $start = Get-Date                              # 기다리기 시작한 시각
    while ($true) {
        Start-Sleep -Milliseconds 500
        $lines = @(& $adb logcat -d -s SunstoneVoice:I | Where-Object { $_ -match "말: " })   # 지금까지 한 말
        for ($i = $shown; $i -lt $lines.Count; $i++) {
            Write-Host ("  Sunstone: " + ($lines[$i] -replace "^.*말: ", "")) -ForegroundColor Cyan
            $quietSince = Get-Date
        }
        $shown = $lines.Count
        $quiet = ((Get-Date) - $quietSince).TotalSeconds   # 새 말 없이 지난 초
        $waited = ((Get-Date) - $start).TotalSeconds       # 기다린 초
        if (($shown -gt 0 -and $quiet -ge 2) -or $waited -ge 15) { break }
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
        & $adb logcat -c
        if ($text.StartsWith("/")) {
            $word, $arg = $text.Substring(1).Split(" ", 2)
            switch ($word) {
                "gear" {
                    $g = $gears[[string]$arg]
                    if ($g) { Send-Console "/vehicle/gear?gear=$g" } else { Write-Host "  기어는 p r n d 중 하나" }
                }
                "speed" { Send-Console "/vehicle/controls?speed=$arg" }
                default { Send-Demo $text.Substring(1) }
            }
        } else {
            Send-Demo "say $text"
        }
        Show-Replies
    }
} finally {
    Stop-Job $heartbeat; Remove-Job $heartbeat -Force
}
