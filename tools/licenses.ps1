# Shows the Android SDK licences so the owner can read and accept them.
. "$PSScriptRoot\env.ps1"
& sdkmanager.bat --sdk_root=$env:ANDROID_HOME --licenses
Write-Host ''
Write-Host 'Done. You can close this tab.'
