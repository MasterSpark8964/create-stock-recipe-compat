$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user'
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat @args
    exit $LASTEXITCODE
} finally { Pop-Location }
