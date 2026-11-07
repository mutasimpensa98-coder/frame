# Frame Glass build tool. Windows PowerShell 5.1 compatible.
#   .\fg.ps1 ship      check the update, raise the version, build, verify, update the app on the phone, compile, launch
#   .\fg.ps1 qa        same for the qa build type
#   .\fg.ps1 build     build and verify only
#   .\fg.ps1 phone     reconnect adb and print phone facts
#   .\fg.ps1 doctor    check every tool version
#   .\fg.ps1 gate N    run the [AUTO] checks of gate N
param(
    [Parameter(Position = 0)][string]$Command = 'ship',
    [Parameter(Position = 1)][string]$Arg = ''
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tools\env.ps1"
Set-Location $PSScriptRoot

$Pkg = 'com.frameglass.player'
$SizeLimitMb = 60

function Fail($m) { Write-Host "FAILED: $m" -ForegroundColor Red; exit 1 }

function Native {
    param([scriptblock]$Run, [string]$What)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $Run | ForEach-Object { Write-Host $_ }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $old
    if ($code -ne 0) { Fail "$What (exit $code)" }
}

# Windows PowerShell turns a redirected stderr line into a terminating error under 'Stop', and tools such
# as java, adb and sdkmanager write normal output there. Returns stdout and stderr as plain text.
function Text {
    param([scriptblock]$Run)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Run 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $old }
}

function Build-Apk([string]$Type) {
    $task = if ($Type -eq 'qa') { 'assembleQa' } else { 'assembleRelease' }
    Native { & .\gradlew.bat $task --console=plain } "gradle $task"
    $dir = "app\build\outputs\apk\$Type"
    $apk = Get-ChildItem $dir -Filter *.apk | Select-Object -First 1
    if (-not $apk) { Fail "no APK in $dir" }
    Native { & apksigner.bat verify --print-certs $apk.FullName } 'apksigner'
    Native { & zipalign.exe -c -P 16 4 $apk.FullName } 'zipalign 16 KB'
    $mb = [math]::Round($apk.Length / 1MB, 1)
    Write-Host "APK $($apk.Name): $mb MB"
    if ($mb -gt $SizeLimitMb) { Fail "APK is over $SizeLimitMb MB" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($apk.FullName)
    $libs = $zip.Entries | Where-Object { $_.FullName -like 'lib/*' } | ForEach-Object { $_.FullName }
    $zip.Dispose()
    $libs | ForEach-Object { Write-Host "  $_" }
    if ($libs | Where-Object { $_ -notlike 'lib/arm64-v8a/*' }) { Fail 'non arm64-v8a library in APK' }
    return $apk.FullName
}

function Phone-Ok {
    $s = Text { & adb.exe get-state }
    if ($s -notcontains 'device') { Fail 'phone is not connected. Check Wireless debugging, then run: adb connect <ip>:<port>' }
}

# Every ship raises versionCode, so the phone always takes a new build as an update.
function Next-Version {
    $file = "$PSScriptRoot\version.properties"
    $lines = Get-Content $file
    $code = [int](($lines | Where-Object { $_ -match '^versionCode=' }) -replace '^versionCode=', '')
    $next = $code + 1
    ($lines -replace '^versionCode=.*', "versionCode=$next") | Set-Content $file -Encoding ascii
    Write-Host "versionCode $code to $next"
}

# Keeps each shipped build under a readable name, for example "Frame Glass 1.0.101.apk".
function Save-Release([string]$File) {
    $v = ConvertFrom-StringData (Get-Content "$PSScriptRoot\version.properties" -Raw)
    $target = "D:\FrameGlass\releases\Frame Glass $($v.versionName).$($v.versionCode).apk"
    Copy-Item -LiteralPath $File -Destination $target
    Write-Host "saved $target"
}

# firstInstallTime changes only on a fresh install, so it proves an update kept the app's data.
function Package-Facts([string]$Id) {
    $text = Text { & adb.exe shell dumpsys package $Id }
    $pick = {
        param($pattern)
        $m = $text | Select-String $pattern | Select-Object -First 1
        if ($m) { $m.Matches[0].Groups[1].Value.Trim() }
    }
    [pscustomobject]@{
        First = & $pick 'firstInstallTime=(.+)$'
        Code  = & $pick 'versionCode=(\d+)'
        Name  = & $pick 'versionName=(\S+)'
    }
}

function Install-Apk([string]$File, [string]$Id) {
    Phone-Ok
    $before = Package-Facts $Id
    Native { & adb.exe install -r $File } 'adb install. Never uninstall to get past this, find the cause'
    Native { & adb.exe shell cmd package compile -m speed-profile -f $Id } 'compile'
    Text { & adb.exe shell monkey -p $Id -c android.intent.category.LAUNCHER 1 } | Out-Null
    $after = Package-Facts $Id
    if (-not $before.First) {
        Write-Host "first install of $Id $($after.Name) ($($after.Code))"
    } elseif ($before.First -ne $after.First) {
        Fail "$Id came back as a fresh install, not an update"
    } else {
        Write-Host "updated $Id $($before.Name) ($($before.Code)) to $($after.Name) ($($after.Code)), first installed $($after.First)"
    }
}

switch ($Command) {
    'build' { Build-Apk 'release' | Out-Null }
    'ship' {
        Phone-Ok
        Native { & python -I tools\check_update.py } 'update check'
        Next-Version
        $a = Build-Apk 'release'
        Install-Apk $a $Pkg
        Save-Release $a
    }
    'qa' {
        $a = Build-Apk 'qa'
        Install-Apk $a "$Pkg.qa"
        & adb.exe shell appops set --uid "$Pkg.qa" MANAGE_EXTERNAL_STORAGE allow
    }
    'phone' {
        & adb.exe devices -l
        Phone-Ok
        foreach ($p in 'ro.product.model', 'ro.build.display.id', 'ro.build.version.sdk', 'ro.build.version.security_patch') {
            "{0} = {1}" -f $p, (& adb.exe shell getprop $p)
        }
        'page size = ' + (& adb.exe shell getconf PAGE_SIZE)
        & adb.exe shell df -h /sdcard | Select-Object -Last 1
    }
    'doctor' {
        Text { & java -version } | Select-Object -First 1
        Text { & .\gradlew.bat --version } | Select-String 'Gradle |Kotlin:|JVM:'
        Text { & adb.exe version } | Select-Object -First 1
        Text { & sdkmanager.bat --sdk_root=$env:ANDROID_HOME --list_installed } | Select-String 'platforms|build-tools|platform-tools'
    }
    'gate' {
        if ($Arg -ne '0') { Fail "gate $Arg is not written yet" }
        Native { & python -I tools\matrix.py gate0 } 'gate 0'
    }
    default { Fail "unknown command $Command" }
}
