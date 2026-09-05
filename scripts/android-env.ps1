$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJava = Get-ChildItem -LiteralPath (Join-Path $taskRoot '.tools\java') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
    Select-Object -First 1
if (-not $taskJava) { throw 'Install JDK 17+ under .tools/java or configure your own toolchain.' }
$env:JAVA_HOME = $taskJava.FullName
$env:ANDROID_HOME = Join-Path $taskRoot '.tools\android-sdk'
$env:ANDROID_USER_HOME = Join-Path $taskRoot '.tools\android-user'
$env:GRADLE_USER_HOME = Join-Path $taskRoot '.tools\gradle-user'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

