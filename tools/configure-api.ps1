<#
목적: 바깥 API 키를 저장소에 올리지 않는 local.properties에서 읽어 기기 안 설정 파일
      (api.properties)로 보낸다. 키는 화면·로그·APK에 넣지 않는다.
      - debug 앱: 앱 비공개 폴더(files/api.properties)에 넣는다(run-as)
      - release 앱: run-as가 안 되므로 앱 바깥 저장소 폴더
        (Android/data/com.westone.sunstone/files/api.properties)에 넣는다
사용: 앱을 한 번 실행한 뒤 .\tools\configure-api.ps1
#>
param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
$ErrorActionPreference = 'Stop'
$package = 'com.westone.sunstone'
$configPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'local.properties'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'local.properties에 API 키를 먼저 넣어 주세요.' }
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($configPath)) {
    if ($line -match '^\s*(sunstone\.[A-Za-z.]+)\s*=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
if (-not $settings['sunstone.kakao.restKey'] -or $settings['sunstone.kakao.restKey'] -notmatch '^[A-Za-z0-9_-]+$') {
    throw 'sunstone.kakao.restKey가 없거나 형식이 잘못됐어요. 값은 보이지 않습니다.'
}
$fuelCode = $settings['sunstone.fuel.code']
if (-not $fuelCode) { $fuelCode = 'B027' }
if ($fuelCode -notin @('B027', 'D047')) { throw '유종은 B027(휘발유) 또는 D047(경유)로 넣어 주세요.' }
$lines = @("sunstone.kakao.restKey=$($settings['sunstone.kakao.restKey'])", "sunstone.fuel.code=$fuelCode")
$opinet = $settings['sunstone.opinet.key']
if ($opinet -and $opinet -match '^[A-Za-z0-9_-]+$') { $lines += "sunstone.opinet.key=$opinet" }
else { Write-Output '오피넷 키가 없어 주유소는 카카오 검색(가격 없음)만 씁니다.' }

$deviceUser = (& $Adb shell am get-current-user | Out-String).Trim()
if ($deviceUser -notmatch '^\d+$') { throw '지금 Android 사용자를 알 수 없어요. 기기 연결을 확인해 주세요.' }

# 키가 명령줄에 남지 않게 표준 입력으로 넘긴다. 출력에는 키가 섞일 수 있어 버린다.
function Send-Lines([string]$arguments) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $Adb
    $start.Arguments = $arguments
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($start)
    $outputTask = $process.StandardOutput.ReadToEndAsync()
    $errorTask = $process.StandardError.ReadToEndAsync()
    # PowerShell 5.1은 표준 입력 맨 앞에 BOM을 붙인다. 첫 줄을 주석으로 써서 키 이름에 붙지 않게 한다
    $process.StandardInput.Write("# Sunstone API keys`n")
    foreach ($line in $lines) { $process.StandardInput.Write("$line`n") }
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(10000)) { $process.Kill(); return $false }
    $null = $outputTask.Result
    $null = $errorTask.Result
    $ok = $process.ExitCode -eq 0
    $process.Dispose()
    return $ok
}

if (Send-Lines "shell -T run-as $package --user $deviceUser tee files/api.properties") {
    Write-Output '키를 앱 비공개 폴더에 넣었습니다(debug 앱). 값은 보이지 않습니다.'
    exit 0
}
$folder = "/storage/emulated/$deviceUser/Android/data/$package/files"
$null = & $Adb shell "mkdir -p $folder" 2>&1
if (Send-Lines "shell -T tee $folder/api.properties") {
    Write-Output '키를 앱 바깥 저장소 폴더에 넣었습니다(release 앱). 값은 보이지 않습니다.'
    exit 0
}
throw '키를 넣지 못했어요. 기기 연결과 앱 설치·1회 실행을 확인해 주세요.'
