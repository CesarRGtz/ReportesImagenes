[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9 _-]+$')]
    [string]$OutputDirectory = "paquete"
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$jdkBin = "C:\Program Files\Java\jdk-26.0.2.1\bin"
$buildRoot = Join-Path $projectRoot "build-exe"
$classesDir = Join-Path $buildRoot "classes"
$inputDir = Join-Path $buildRoot "input"
$outputRoot = Join-Path $projectRoot $OutputDirectory
$applicationDir = Join-Path $outputRoot "Reportes TEOSA"

if (-not (Test-Path -LiteralPath (Join-Path $projectRoot "pom.xml"))) {
    throw "Este script debe permanecer en la carpeta principal del proyecto."
}

foreach ($tool in @("javac.exe", "jar.exe", "jpackage.exe")) {
    $toolPath = Join-Path $jdkBin $tool
    if (-not (Test-Path -LiteralPath $toolPath)) {
        throw "No se encontró $toolPath. Verifica la instalación del JDK."
    }
}

function Resolve-Dependency {
    param(
        [Parameter(Mandatory = $true)][string]$FileName,
        [Parameter(Mandatory = $true)][string]$MavenPath
    )

    $localPath = Join-Path $projectRoot $FileName
    if (Test-Path -LiteralPath $localPath) {
        return $localPath
    }
    if (Test-Path -LiteralPath $MavenPath) {
        return $MavenPath
    }
    throw "No se encontró la dependencia $FileName junto al proyecto ni en la caché de Maven."
}

$dependencies = @(
    Resolve-Dependency "javafx-base-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-base\25\javafx-base-25-win.jar"
    Resolve-Dependency "javafx-graphics-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-graphics\25\javafx-graphics-25-win.jar"
    Resolve-Dependency "javafx-controls-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-controls\25\javafx-controls-25-win.jar"
    Resolve-Dependency "javafx-fxml-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-fxml\25\javafx-fxml-25-win.jar"
    Resolve-Dependency "openpdf-1.3.39.jar" "C:\Users\Usuario\.m2\repository\com\github\librepdf\openpdf\1.3.39\openpdf-1.3.39.jar"
    Resolve-Dependency "gson-2.14.0.jar" "C:\Users\Usuario\.m2\repository\com\google\code\gson\gson\2.14.0\gson-2.14.0.jar"
)

foreach ($dependency in $dependencies) {
    if (-not (Test-Path -LiteralPath $dependency)) {
        throw "No se encontró la dependencia: $dependency"
    }
}

# Solo elimina resultados generados anteriormente dentro de este proyecto.
foreach ($generatedPath in @($buildRoot, $applicationDir)) {
    $fullPath = [System.IO.Path]::GetFullPath($generatedPath)
    if (-not $fullPath.StartsWith($projectRoot + "\", [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Ruta de compilación no válida: $fullPath"
    }
    if (Test-Path -LiteralPath $fullPath) {
        Remove-Item -LiteralPath $fullPath -Recurse -Force
    }
}

New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$modulePath = $dependencies -join ";"
$sources = Get-ChildItem -LiteralPath (Join-Path $projectRoot "src\main\java") `
    -Filter "*.java" -Recurse | ForEach-Object FullName

Write-Host "Compilando el proyecto..." -ForegroundColor Cyan
& (Join-Path $jdkBin "javac.exe") `
    --release 25 `
    -encoding UTF-8 `
    --module-path $modulePath `
    -d $classesDir `
    $sources

Copy-Item -Path (Join-Path $projectRoot "src\main\resources\*") `
    -Destination $classesDir -Recurse -Force

Write-Host "Creando el archivo de la aplicación..." -ForegroundColor Cyan
$applicationJar = Join-Path $inputDir "app-prototipo.jar"
& (Join-Path $jdkBin "jar.exe") `
    --create `
    --file $applicationJar `
    --main-class com.teosa.app.prototipo.Launcher `
    -C $classesDir .

foreach ($dependency in $dependencies) {
    Copy-Item -LiteralPath $dependency -Destination $inputDir -Force
}

Write-Host "Generando el ejecutable autocontenido..." -ForegroundColor Cyan
& (Join-Path $jdkBin "jpackage.exe") `
    --type app-image `
    --dest $outputRoot `
    --name "Reportes TEOSA" `
    --input $inputDir `
    --main-jar "app-prototipo.jar" `
    --main-class "com.teosa.app.prototipo.Launcher" `
    --java-options "--enable-native-access=ALL-UNNAMED" `
    --app-version "1.0.0" `
    --vendor "TEOSA"

$executable = Join-Path $applicationDir "Reportes TEOSA.exe"
if (-not (Test-Path -LiteralPath $executable)) {
    throw "El empaquetado terminó sin producir el ejecutable esperado."
}

Write-Host ""
Write-Host "Ejecutable generado correctamente:" -ForegroundColor Green
Write-Host $executable -ForegroundColor Green
Write-Host "Distribuye toda la carpeta 'Reportes TEOSA', no solamente el archivo .exe."
