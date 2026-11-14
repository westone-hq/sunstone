<#
목적: 기존 녹음본을 지우지 않고 세차 A/B/C 기본 시연 문장만 추가한다.
      동적인 지역·숫자 안내는 기존 Pleos TTS가 읽는다.
#>
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$voiceRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\src\main\assets\voice'
$prefix = '연료가 18%예요. 가는 길에 말죽거리주유소가 있어요.'
$weatherLines = @(
    "$prefix 세차 안 하신 지 36일 됐고, 일주일간 비 예보가 없어서 세차도 추천드려요.",
    "$prefix 세차 안 하신 지 36일 됐지만, 일주일간 비 예보가 있어서 세차는 추천드리지 않아요.",
    "$prefix 현재 비 예보를 확인할 수 없어 세차 추천은 보류할게요.",
    '현재 날씨를 확인할 수 없어요. 잠시 후 다시 요청해 주세요.',
    '현재 위치를 확인할 수 없어 날씨를 조회하지 못했어요.'
)
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $synth.SelectVoice('Microsoft Heami Desktop')
    foreach ($line in $weatherLines) {
        $digest = [Security.Cryptography.MD5]::Create()
        try { $hash = -join ($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($line)) | ForEach-Object { $_.ToString('x2') }) }
        finally { $digest.Dispose() }
        $outputFile = Join-Path $voiceRoot "$hash.wav"
        if (Test-Path -LiteralPath $outputFile) { continue }
        $synth.SetOutputToWaveFile($outputFile)
        $synth.Speak($line)
        $synth.SetOutputToNull()
        Write-Output "$hash.wav"
    }
} finally { $synth.Dispose() }
