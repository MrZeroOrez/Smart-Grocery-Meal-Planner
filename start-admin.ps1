$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:ADMIN_EMAIL = Read-Host "Administrator email"
$secret = Read-Host "Administrator password (12-72 UTF-8 bytes)" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try { $env:ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
try { & "$PSScriptRoot\start.cmd" }
finally { Remove-Item Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue; Remove-Item Env:ADMIN_EMAIL -ErrorAction SilentlyContinue }
