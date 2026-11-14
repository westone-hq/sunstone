<#
목적: 설치된 Sunstone_Pleos 가상 기기를 켜고 부팅 후 Sunstone을 연다.
      APK·키 설정은 최초 설치 때 준비한 기기 데이터를 그대로 사용한다.
#>
$ErrorActionPreference = 'Stop'
$sdkForSunstone = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adbForSunstone = Join-Path $sdkForSunstone 'platform-tools\adb.exe'
$emulatorForSunstone = Join-Path $sdkForSunstone 'emulator\emulator.exe'
if (-not (Test-Path -LiteralPath $emulatorForSunstone)) { throw 'Android Emulator가 설치되어 있지 않아요.' }
$deviceForSunstone = $null
foreach ($line in (& $adbForSunstone devices)) {
    if ($line -match '^(emulator-\d+)\s+device$') {
        $candidate = $matches[1]
        $name = (& $adbForSunstone -s $candidate emu avd name 2>$null | Select-Object -First 1)
        if ($name -eq 'Sunstone_Pleos') { $deviceForSunstone = $candidate; break }
    }
}
if (-not $deviceForSunstone) {
    Start-Process -FilePath $emulatorForSunstone -ArgumentList @(
        '-avd', 'Sunstone_Pleos', '-no-snapshot-load', '-no-snapshot-save', '-feature', '-Vulkan', '-allow-host-audio'
    ) -WindowStyle Normal
}
$deadlineForSunstone = [DateTime]::UtcNow.AddMinutes(4)
while ([DateTime]::UtcNow -lt $deadlineForSunstone) {
    foreach ($line in (& $adbForSunstone devices)) {
        if ($line -match '^(emulator-\d+)\s+device$') {
            $candidate = $matches[1]
            $name = (& $adbForSunstone -s $candidate emu avd name 2>$null | Select-Object -First 1)
            if ($name -eq 'Sunstone_Pleos') { $deviceForSunstone = $candidate; break }
        }
    }
    if ($deviceForSunstone) {
        $boot = (& $adbForSunstone -s $deviceForSunstone shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($boot -eq '1') {
            & $adbForSunstone -s $deviceForSunstone shell am start -n com.westone.sunstone/.ui.MainActivity
            exit $LASTEXITCODE
        }
    }
    Start-Sleep -Seconds 3
}
throw '부팅이 아직 끝나지 않았어요. 에뮬레이터 창을 확인해 주세요.'
