# Windows version of build-all.sh. Usage: .\build-all.ps1 [filter]
param([string]$Filter = "")
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$version = if ($env:MOD_VERSION) { $env:MOD_VERSION } else { (Select-String -Path gradle.properties -Pattern '^modVersion=(.*)$').Matches[0].Groups[1].Value }
New-Item -ItemType Directory -Force dist | Out-Null
Remove-Item dist\*.jar -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon "-PmodVersion=$version" :core:test
$failed = @()
foreach ($dir in Get-ChildItem targets -Directory) {
  if ($dir.Name -like "common*") { continue }
  if ($Filter -and $dir.Name -notlike "*$Filter*") { continue }
  Write-Host "==> $($dir.Name)"
  Push-Location $dir.FullName
  try {
    .\gradlew.bat --no-daemon "-PmodVersion=$version" build
    if ($LASTEXITCODE -ne 0) { throw "build failed" }
    Get-ChildItem build\libs\*.jar | Where-Object { $_.Name -notmatch '-(dev|sources|all|shadow)\.jar$' } | Copy-Item -Destination ..\..\dist
  } catch { $failed += $dir.Name } finally { Pop-Location }
}
if ($failed.Count) { Write-Error "FAILED targets: $($failed -join ', ')"; exit 1 }
