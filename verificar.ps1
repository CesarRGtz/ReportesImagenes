param([string]$BuildDirectory = 'build-clean', [string]$TestFilter = '*')
$ErrorActionPreference='Stop'
$jdkBin='C:\Program Files\Java\jdk-26.0.2.1\bin'
$classes=Join-Path $PSScriptRoot "$BuildDirectory/classes"
$testClasses=Join-Path $PSScriptRoot "$BuildDirectory/test-classes"
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
$classpath="$classes;$(Join-Path $PSScriptRoot "$BuildDirectory/input/*")"
$sources=Get-ChildItem "$PSScriptRoot/src/test/java" -Filter '*.java' -Recurse | ForEach-Object FullName
& "$jdkBin/javac.exe" -encoding UTF-8 -cp $classpath -d $testClasses $sources
if($LASTEXITCODE -ne 0){throw 'Falló la compilación de pruebas'}
$failures=@()
foreach($source in $sources){
 $name=[IO.Path]::GetFileNameWithoutExtension($source)
 # Helper invoked by BorderAtPageEndSmokeTest with its generated PDF.
 if($name -eq 'PageBorderSmokeTest'){continue}
 if($name -notlike $TestFilter){continue}
 Write-Host "Verificando $name"
 & "$jdkBin/java.exe" --enable-native-access=ALL-UNNAMED -cp "$testClasses;$classpath" "com.teosa.app.prototipo.$name"
 if($LASTEXITCODE -ne 0){$failures+=$name}
}
if($failures.Count -gt 0){throw "Pruebas fallidas: $($failures -join ', ')"}
