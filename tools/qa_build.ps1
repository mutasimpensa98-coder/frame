# Builds the qa APK and installs it on the phone. Output stays in D:\FrameGlass\logs\build-qa.log.
. "$PSScriptRoot\env.ps1"
Set-Location "$PSScriptRoot\.."
cmd /c ".\gradlew.bat assembleQa --console=plain > D:\FrameGlass\logs\build-qa.log 2>&1"
if ($LASTEXITCODE -ne 0) {
    Select-String -Path D:\FrameGlass\logs\build-qa.log -Pattern '^e: |wrong:|FAILED|Missing class' |
        Where-Object { $_.Line -notmatch 'UP-TO-DATE' } | Select-Object -First 20 |
        ForEach-Object { $_.Line.Substring(0, [Math]::Min(240, $_.Line.Length)) }
    exit 1
}
cmd /c "adb.exe install -r app\build\outputs\apk\qa\app-qa.apk 2>&1"
if ($LASTEXITCODE -ne 0) { exit 1 }
cmd /c "adb.exe shell appops set --user 0 com.frameglass.player.qa MANAGE_EXTERNAL_STORAGE allow 2>&1"
'build and install ok'
