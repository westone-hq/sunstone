<#
목적: 사용자 지정 키를 Git 제외 local.properties에서 읽어 debug 앱의 비공개 파일로 보낸다.
      키는 명령줄·로그·APK에 넣지 않는다.
#>
param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
$ErrorActionPreference = 'Stop'
$configPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'local.properties'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'local.properties에 API 키를 먼저 설정해 주세요.' }
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($configPath)) {
    if ($line -match '^\s*(sunstone\.[A-Za-z.]+)\s*=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
foreach ($keyName in @('sunstone.kakao.restKey')) {
    if (-not $settings[$keyName] -or $settings[$keyName] -notmatch '^[A-Za-z0-9_-]+$') {
        throw "$keyName 설정이 없거나 형식이 잘못됐어요. 값은 출력하지 않습니다."
    }
}
$fuelCode = $settings['sunstone.fuel.code']
if (-not $fuelCode) { $fuelCode = 'B027' }
if ($fuelCode -notin @('B027', 'D047')) { throw '유종은 B027 또는 D047로 설정해 주세요.' }
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = $Adb
$deviceUser = (& $Adb shell am get-current-user | Out-String).Trim()
if ($deviceUser -notmatch '^\d+$') { throw '현재 Android 사용자 확인 실패' }
$start.Arguments = "shell -T run-as com.westone.sunstone --user $deviceUser tee files/api.properties"
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$process = [Diagnostics.Process]::Start($start)
$outputTask = $process.StandardOutput.ReadToEndAsync() # tee 출력에는 키가 있으므로 절대로 표시하지 않는다
$errorTask = $process.StandardError.ReadToEndAsync()
$process.StandardInput.WriteLine("sunstone.kakao.restKey=$($settings['sunstone.kakao.restKey'])")
$process.StandardInput.WriteLine("sunstone.fuel.code=$fuelCode")
$process.StandardInput.Close()
if (-not $process.WaitForExit(10000)) { $process.Kill(); throw '로컬 설정 적용 시간 초과' }
$null = $outputTask.Result
$null = $errorTask.Result
if ($process.ExitCode -ne 0) { throw '설정 적용 실패. 기기 연결·debug 앱 설치·앱 1회 실행 상태를 확인해 주세요.' }
$process.Dispose()
$verify = New-Object System.Diagnostics.ProcessStartInfo
$verify.FileName = $Adb
$verify.Arguments = "exec-out run-as com.westone.sunstone --user $deviceUser cat files/api.properties"
$verify.UseShellExecute = $false
$verify.CreateNoWindow = $true
$verify.RedirectStandardOutput = $true
$verify.RedirectStandardError = $true
$checkProcess = [Diagnostics.Process]::Start($verify)
$privateContents = $checkProcess.StandardOutput.ReadToEnd()
$null = $checkProcess.StandardError.ReadToEnd()
$checkProcess.WaitForExit()
$checkProcess.Dispose()
foreach ($keyName in @('sunstone.kakao.restKey')) {
    if (-not $privateContents.Contains("$keyName=$($settings[$keyName])")) {
        throw '기기 설정 파일 검증 실패. 키 값은 출력하지 않습니다.'
    }
}
Write-Output '로컬 설정을 앱 비공개 파일에 적용했습니다. 키는 출력하지 않았습니다.'
