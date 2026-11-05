[CmdletBinding()]
param(
    [ValidateSet("win-x64", "win-arm64")]
    [string]$Runtime = "win-x64",
    [switch]$FrameworkDependent
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Project = Join-Path $Root "apps/windows/MouseBoard.Windows.csproj"
$Apk = Join-Path $Root "dist/MouseBoard-Android.apk"
$Output = Join-Path $Root "dist/MouseBoard-Windows-$Runtime"
$Archive = Join-Path $Root "dist/MouseBoard-Windows-$Runtime.zip"

if (-not (Test-Path $Apk -PathType Leaf)) {
    throw "Build the Android app first; missing $Apk"
}
if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw "Install the .NET 10 SDK, then run this script again."
}

Push-Location (Join-Path $Root "apps/windows")
try {
    dotnet run --project tests/MouseBoard.Windows.Checks.csproj --configuration Release
    if ($LASTEXITCODE -ne 0) { throw "Windows companion checks failed." }

    $SelfContained = if ($FrameworkDependent) { "false" } else { "true" }
    if (Test-Path $Output) { Remove-Item $Output -Recurse -Force }
    dotnet publish $Project --configuration Release --runtime $Runtime `
        --self-contained $SelfContained --output $Output `
        "-p:InstallerApk=$Apk" "-p:PublishSingleFile=false"
    if ($LASTEXITCODE -ne 0) { throw "Windows companion publish failed." }
    if (-not (Test-Path (Join-Path $Output "MouseBoard.exe"))) {
        throw "Publish did not produce MouseBoard.exe."
    }
    $BundledApk = Join-Path $Output "Assets/MouseBoard-Android.apk"
    if (-not (Test-Path $BundledApk)) { throw "Published bundle is missing the phone installer." }
    if ((Get-FileHash $Apk -Algorithm SHA256).Hash -ne (Get-FileHash $BundledApk -Algorithm SHA256).Hash) {
        throw "Bundled phone installer differs from the current Android build."
    }
    Compress-Archive -Path "$Output/*" -DestinationPath $Archive -Force
    Write-Host "Built: $Archive"
    Write-Host "Run MouseBoard.exe from the extracted folder."
} finally {
    Pop-Location
}
