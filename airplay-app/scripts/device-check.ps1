# Device Diagnostics Script for Wall Panel Audio Receiver
# Run this before installing to verify device requirements

param(
    [string]$DeviceIP = "172.16.1.26",
    [int]$Port = 5555
)

$ErrorActionPreference = "Continue"
$Device = "$DeviceIP`:$Port"
$Results = @()

function Write-Header {
    param([string]$Title)
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host $Title -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

function Write-Check {
    param(
        [string]$Name,
        [bool]$Pass,
        [string]$Details = ""
    )
    $symbol = if ($Pass) { "✓" } else { "✗" }
    $color = if ($Pass) { "Green" } else { "Red" }
    Write-Host "  $symbol $Name" -ForegroundColor $color -NoNewline
    if ($Details) {
        Write-Host " - $Details" -NoNewline
    }
    Write-Host ""
    $script:Results += [PSCustomObject]@{
        Check = $Name
        Pass = $Pass
        Details = $Details
    }
}

Write-Header "Device Diagnostics for Tuya G10"
Write-Host "Connecting to $Device..."

# Connect to device
$connectResult = adb connect $Device 2>&1
if ($connectResult -match "connected") {
    Write-Check "ADB Connection" $true "Connected to $DeviceIP"
} else {
    Write-Check "ADB Connection" $false "Failed to connect"
    Write-Host "`nCannot proceed without ADB connection." -ForegroundColor Red
    exit 1
}

# Device information
Write-Header "Device Information"
$model = adb -s $Device shell getprop ro.product.model
$android = adb -s $Device shell getprop ro.build.version.release
$sdk = adb -s $Device shell getprop ro.build.version.sdk
$buildType = adb -s $Device shell getprop ro.build.type

Write-Host "  Model: $model"
Write-Host "  Android: $android (API $sdk)"
Write-Host "  Build Type: $buildType"

Write-Check "Android Version" ($android -match "8\.1") "Expected 8.1"
Write-Check "SDK Level" ($sdk -eq "27") "Expected 27"

# Root access
Write-Header "Root Access"
$rootResult = adb -s $Device shell su -c id 2>&1
$hasRoot = $rootResult -match "uid=0"
Write-Check "Root Available" $hasRoot $rootResult

# SELinux
Write-Header "SELinux Status"
$selinux = adb -s $Device shell getenforce 2>&1
$selinuxOk = ($selinux -match "Permissive") -or ($selinux -match "Disabled")
Write-Check "SELinux" $selinuxOk "Status: $selinux"

# CPU Architecture
Write-Header "CPU Architecture"
$abilist = adb -s $Device shell getprop ro.product.cpu.abilist
$abi = adb -s $Device shell getprop ro.product.cpu.abi
Write-Host "  ABI List: $abilist"
Write-Host "  Primary ABI: $abi"
$hasArm = ($abilist -match "arm64-v8a") -or ($abilist -match "armeabi-v7a")
Write-Check "ARM Architecture" $hasArm "ARM64 or ARMv7 required"

# RAM
Write-Header "Memory"
$freeOutput = adb -s $Device shell free -m 2>&1
$freeLine = $freeOutput | Select-String "Mem:" | Select-Object -First 1
if ($freeLine) {
    $parts = $freeLine -split '\s+'
    $total = $parts[1]
    $available = $parts[6]
    Write-Host "  Total: ${total} MB"
    Write-Host "  Available: ${available} MB"
    $ramOk = [int]$available -gt 100
    Write-Check "Available RAM" $ramOk "Requires >100 MB"
} else {
    Write-Check "Available RAM" $false "Could not parse free output"
}

# Network
Write-Header "Network Configuration"
$ipResult = adb -s $Device shell "ip addr show wlan0 | grep 'inet '"
$hasIp = $ipResult -match "inet"
Write-Host "  $ipResult"
Write-Check "Network Interface" $hasIp "wlan0 configured"

# Multicast
Write-Header "Multicast Support"
$mcResult = adb -s $Device shell ip maddress show wlan0 2>&1
$hasMc = $mcResult -match "224\.|239\."
Write-Check "Multicast" $hasMc "Multicast addresses present"

# Port availability
Write-Header "Port Availability"
$ports = @(7000, 319, 320, 8099)
$allPortsFree = $true
foreach ($port in $ports) {
    $portResult = adb -s $Device shell su -c "netstat -lntp 2>/dev/null | grep :$port" 2>&1
    $portFree = -not ($portResult -match ":$port")
    if (-not $portFree) {
        $allPortsFree = $false
    }
    Write-Check "Port $port" $portFree $(if ($portFree) { "Free" } else { "In use" })
}

# Overall summary
Write-Header "Summary"
$passCount = ($Results | Where-Object { $_.Pass }).Count
$totalCount = $Results.Count
$failCount = $totalCount - $passCount

Write-Host "  Passed: $passCount / $totalCount" -ForegroundColor Green
if ($failCount -gt 0) {
    Write-Host "  Failed: $failCount" -ForegroundColor Red
}

if ($failCount -eq 0) {
    Write-Host "`n✓ All checks passed! Device is ready for installation." -ForegroundColor Green
    Write-Host "`nNext steps:"
    Write-Host "  1. Review docs/INSTALL.md for installation instructions"
    Write-Host "  2. Build the APK (see docs/BUILD.md)"
    Write-Host "  3. Install: adb install app-release.apk"
} else {
    Write-Host "`n✗ Some checks failed. See docs/DEVICE_CHECK.md for troubleshooting." -ForegroundColor Yellow
    Write-Host "`nFailed checks:"
    $Results | Where-Object { -not $_.Pass } | ForEach-Object {
        Write-Host "  - $($_.Check): $($_.Details)" -ForegroundColor Red
    }
}

# Save results
$logFile = "device-diagnostics-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
$Results | Export-Csv -Path $logFile -NoTypeInformation
Write-Host "`nDetailed results saved to: $logFile"

# Disconnect
adb disconnect $Device | Out-Null
