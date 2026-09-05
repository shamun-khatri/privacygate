param([string[]]$Tasks = @('assembleDebug'))
. "$PSScriptRoot\android-env.ps1"
Push-Location $taskRoot
try {
    & '.\gradlew.bat' @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
