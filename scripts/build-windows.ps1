[CmdletBinding()]
param(
    [ValidateSet("win-x64", "win-arm64")]
    [string]$Runtime = "win-x64"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
python (Join-Path $PSScriptRoot "package-windows.py") --runtime $Runtime
if ($LASTEXITCODE -ne 0) { throw "Windows packaging failed." }
