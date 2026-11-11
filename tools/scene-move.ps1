<#
작성: 2026-11-11 12:12:00 · 박건준
목적: 시연 시나리오의 차 이동을 에뮬레이터에 넣는다. 에뮬레이터 GPS를 1초마다
      `adb emu geo fix`로 옮기고, 속도·기어는 Virtual Console API로 함께 바꾼다
      (이동 중 D·60km/h, 도착하면 0km/h·P).
      - 1: 시나리오 ① 출장 가는 길. 서울(강남역) 출발 → 시연 주유소
      - 2: 시나리오 ② 아이 등원길. 집 → 유치원(정차) → 집
      장소 좌표는 앱과 같은 app/src/main/assets/places.json에서 읽는다.
인자: -Scenario  1 또는 2
      -StepM     1초에 옮길 거리(m). 기본 17은 60km/h. 리허설에서 줄이려면 키운다
      -PauseSec  ②에서 유치원에 머무는 시간(초)
근거: 측정 — FusedLocation·근접 알림은 geo fix를 1초 안에 따라온다.
      내비 현재 위치는 geo fix를 따라가지 않는다(NMEA 체크섬 오류).
      에뮬레이터에는 경로 재생 adb 명령이 없어 geo fix를 반복한다
      (docs/api-spike.md 내비 실측 절)
미구현: 프로토타입이라 실제 주행 대신 직선으로 옮긴다. 길을 따라가지 않는다
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet("1", "2")][string]$Scenario,
    [double]$StepM = 17,
    [int]$PauseSec = 10
)

$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"   # Android SDK 위치
$adb = "$sdk\platform-tools\adb.exe"      # adb
$console = "http://127.0.0.1:8088"        # Virtual Console
$placesFile = Join-Path $PSScriptRoot "..\app\src\main\assets\places.json"  # 시연 장소 목록
$places = (Get-Content $placesFile -Raw -Encoding UTF8 | ConvertFrom-Json).places
$start1 = @{ lat = 37.4979; lng = 127.0276; name = "서울(강남역)" }   # ① 출발점. 어느 장소 반경에도 들지 않는다

<#
함수: Spot
목적: places.json에서 id로 장소 하나를 꺼낸다.
#>
function Spot([string]$id) {
    $p = $places | Where-Object { $_.id -eq $id }
    if (-not $p) { throw "places.json에 $id 가 없다" }
    return @{ lat = [double]$p.lat; lng = [double]$p.lng; name = $p.name }
}

<#
함수: Invoke-Console
목적: 콘솔 API를 부른다. 브라우저 세션 신호를 먼저 보내 차량 쪽 연결을 살려 둔다.
#>
function Invoke-Console([string]$path) {
    Invoke-RestMethod -Method Post -Uri "$console/browser/session?client=scene-move" | Out-Null
    Invoke-RestMethod -Method Put -Uri "$console$path" | Out-Null
}

<#
함수: Fix
목적: 에뮬레이터 GPS를 한 점으로 옮긴다(geo fix는 경도, 위도 순서).
#>
function Fix($lat, $lng) {
    & $adb emu geo fix ([string]$lng) ([string]$lat) | Out-Null
}

<#
함수: Drive
목적: 한 점에서 다른 점까지 1초마다 StepM씩 직선으로 옮긴다. 출발할 때 D·60km/h,
      도착하면 0km/h·P로 둔다.
식: n = ceil(d / StepM), d는 두 점 사이 거리(m, 위도 1도 111,320m로 근사)
#>
function Drive($from, $to) {
    $dLat = ($to.lat - $from.lat) * 111320                                       # 남북 거리(m)
    $dLng = ($to.lng - $from.lng) * 111320 * [Math]::Cos($from.lat * [Math]::PI / 180)  # 동서 거리(m)
    $dist = [Math]::Sqrt($dLat * $dLat + $dLng * $dLng)                           # 직선 거리(m)
    $n = [Math]::Max(1, [Math]::Ceiling($dist / $StepM))                          # 옮길 횟수
    Write-Host ("{0} → {1}: {2:N0}m, {3}초" -f $from.name, $to.name, $dist, $n)
    Invoke-Console "/vehicle/gear?gear=Drive"
    Invoke-Console "/vehicle/controls?speed=60"
    for ($i = 1; $i -le $n; $i++) {
        $t = $i / $n                                                              # 간 비율
        Fix ($from.lat + ($to.lat - $from.lat) * $t) ($from.lng + ($to.lng - $from.lng) * $t)
        Invoke-RestMethod -Method Post -Uri "$console/browser/session?client=scene-move" | Out-Null
        Start-Sleep -Seconds 1
    }
    Invoke-Console "/vehicle/controls?speed=0"
    Invoke-Console "/vehicle/gear?gear=Park"
    Write-Host ("{0} 도착 ({1})" -f $to.name, (Get-Date -Format "HH:mm:ss"))
}

& $adb forward tcp:8088 tcp:8088 | Out-Null
if ($Scenario -eq "1") {
    Fix $start1.lat $start1.lng
    Start-Sleep -Seconds 2
    Drive $start1 (Spot "gas")
} else {
    $house = Spot "home"
    Fix $house.lat $house.lng
    Start-Sleep -Seconds 2
    Drive $house (Spot "kids")
    Write-Host "유치원에서 $PauseSec 초 정차"
    for ($i = 0; $i -lt $PauseSec; $i++) {
        Invoke-RestMethod -Method Post -Uri "$console/browser/session?client=scene-move" | Out-Null
        Start-Sleep -Seconds 1
    }
    Drive (Spot "kids") $house
}
