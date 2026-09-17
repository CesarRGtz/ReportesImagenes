[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9 _-]+$')]
    [string]$OutputDirectory = "Ejecutables",
    [ValidatePattern('^[A-Za-z0-9 _-]+$')]
    [string]$BuildDirectory = "build-exe",
    [ValidateSet("All", "Reports", "Quotations", "Server")]
    [string]$Mode = "All",
    [switch]$SoloCompilar
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$jdkBin = "C:\Program Files\Java\jdk-26.0.2.1\bin"
$buildRoot = Join-Path $projectRoot $BuildDirectory
$classesDir = Join-Path $buildRoot "classes"
$inputDir = Join-Path $buildRoot "input"
$outputRoot = Join-Path $projectRoot $OutputDirectory
$applications = @(
    @{ Mode = "Reports"; Name = "Reportes TEOSA"; Launcher = "ReportsLauncher" }
    @{ Mode = "Quotations"; Name = "Cotizaciones TEOSA"; Launcher = "QuotationsLauncher" }
    @{ Mode = "Server"; Name = "Servidor TEOSA"; Launcher = "ServerLauncher" }
) | Where-Object { $Mode -eq "All" -or $_.Mode -eq $Mode }

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
        [Parameter(Mandatory = $true)][string]$MavenPath,
        [string]$DownloadUri
    )

    $localPath = Join-Path $projectRoot $FileName
    if (Test-Path -LiteralPath $localPath) {
        return $localPath
    }
    if (Test-Path -LiteralPath $MavenPath) {
        return $MavenPath
    }
    if (-not [string]::IsNullOrWhiteSpace($DownloadUri)) {
        Write-Host "Descargando $FileName..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $DownloadUri -OutFile $localPath
        return $localPath
    }
    throw "No se encontró la dependencia $FileName junto al proyecto ni en la caché de Maven."
}

$dependencies = @(
    Resolve-Dependency "javafx-base-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-base\25\javafx-base-25-win.jar" "https://repo.maven.apache.org/maven2/org/openjfx/javafx-base/25/javafx-base-25-win.jar"
    Resolve-Dependency "javafx-graphics-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-graphics\25\javafx-graphics-25-win.jar" "https://repo.maven.apache.org/maven2/org/openjfx/javafx-graphics/25/javafx-graphics-25-win.jar"
    Resolve-Dependency "javafx-controls-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-controls\25\javafx-controls-25-win.jar" "https://repo.maven.apache.org/maven2/org/openjfx/javafx-controls/25/javafx-controls-25-win.jar"
    Resolve-Dependency "javafx-fxml-25-win.jar" "C:\Users\Usuario\.m2\repository\org\openjfx\javafx-fxml\25\javafx-fxml-25-win.jar" "https://repo.maven.apache.org/maven2/org/openjfx/javafx-fxml/25/javafx-fxml-25-win.jar"
    Resolve-Dependency "openpdf-1.3.39.jar" "C:\Users\Usuario\.m2\repository\com\github\librepdf\openpdf\1.3.39\openpdf-1.3.39.jar" "https://repo.maven.apache.org/maven2/com/github/librepdf/openpdf/1.3.39/openpdf-1.3.39.jar"
    Resolve-Dependency "gson-2.14.0.jar" "C:\Users\Usuario\.m2\repository\com\google\code\gson\gson\2.14.0\gson-2.14.0.jar" "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar"
    Resolve-Dependency "pdfbox-3.0.8.jar" "C:\Users\Usuario\.m2\repository\org\apache\pdfbox\pdfbox\3.0.8\pdfbox-3.0.8.jar" "https://repo.maven.apache.org/maven2/org/apache/pdfbox/pdfbox/3.0.8/pdfbox-3.0.8.jar"
    Resolve-Dependency "pdfbox-io-3.0.8.jar" "C:\Users\Usuario\.m2\repository\org\apache\pdfbox\pdfbox-io\3.0.8\pdfbox-io-3.0.8.jar" "https://repo.maven.apache.org/maven2/org/apache/pdfbox/pdfbox-io/3.0.8/pdfbox-io-3.0.8.jar"
    Resolve-Dependency "fontbox-3.0.8.jar" "C:\Users\Usuario\.m2\repository\org\apache\pdfbox\fontbox\3.0.8\fontbox-3.0.8.jar" "https://repo.maven.apache.org/maven2/org/apache/pdfbox/fontbox/3.0.8/fontbox-3.0.8.jar"
    Resolve-Dependency "commons-logging-1.3.5.jar" "C:\Users\Usuario\.m2\repository\commons-logging\commons-logging\1.3.5\commons-logging-1.3.5.jar" "https://repo.maven.apache.org/maven2/commons-logging/commons-logging/1.3.5/commons-logging-1.3.5.jar"
)

foreach ($dependency in $dependencies) {
    if (-not (Test-Path -LiteralPath $dependency)) {
        throw "No se encontró la dependencia: $dependency"
    }
}

# Solo elimina resultados generados anteriormente dentro de este proyecto.
$generatedPaths = @($buildRoot)
if (-not $SoloCompilar) {
    foreach ($application in $applications) { $generatedPaths += Join-Path $outputRoot $application.Name }
}
# Comprobar bloqueos antes de eliminar cualquier carpeta de una entrega anterior.
foreach ($candidate in $generatedPaths) {
    $checkedPath = [IO.Path]::GetFullPath($candidate)
    if (-not $checkedPath.StartsWith($projectRoot + "\", [StringComparison]::OrdinalIgnoreCase)) { throw "Ruta no válida: $checkedPath" }
    if (Test-Path -LiteralPath $checkedPath) {
        foreach ($file in (Get-ChildItem -LiteralPath $checkedPath -Recurse -File)) {
            try { $handle = [IO.File]::Open($file.FullName, 'Open', 'Read', 'None'); $handle.Dispose() }
            catch { throw "Hay una aplicación abierta o un archivo bloqueado: $($file.FullName). Cierra la aplicación o utiliza otra carpeta de salida." }
        }
    }
}
foreach ($generatedPath in $generatedPaths) {
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
if (-not $SoloCompilar) {
    New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
}

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
if ($LASTEXITCODE -ne 0) { throw "La compilación falló." }

Copy-Item -Path (Join-Path $projectRoot "src\main\resources\*") `
    -Destination $classesDir -Recurse -Force

foreach ($dependency in $dependencies) {
    Copy-Item -LiteralPath $dependency -Destination $inputDir -Force
}

if ($SoloCompilar) {
    Write-Host "Compilacion para el IDE completada correctamente." -ForegroundColor Green
    exit 0
}

Write-Host "Creando el archivo de la aplicación..." -ForegroundColor Cyan
$applicationJar = Join-Path $inputDir "app-prototipo.jar"
& (Join-Path $jdkBin "jar.exe") `
    --create `
    --file $applicationJar `
    --main-class com.teosa.app.prototipo.Launcher `
    -C $classesDir .

foreach ($application in $applications) {
    Write-Host "Generando $($application.Name)..." -ForegroundColor Cyan
    & (Join-Path $jdkBin "jpackage.exe") `
        --type app-image `
        --dest $outputRoot `
        --name $application.Name `
        --input $inputDir `
        --main-jar "app-prototipo.jar" `
        --main-class "com.teosa.app.prototipo.$($application.Launcher)" `
        --java-options "--enable-native-access=ALL-UNNAMED" `
        --app-version "2.0.0" `
        --vendor "TEOSA"
    if ($LASTEXITCODE -ne 0) { throw "Falló el empaquetado de $($application.Name)." }
    $executable = Join-Path (Join-Path $outputRoot $application.Name) "$($application.Name).exe"
    if (-not (Test-Path -LiteralPath $executable)) { throw "No se produjo $executable" }
    Write-Host $executable -ForegroundColor Green
}
Write-Host "Distribuye la carpeta completa de cada aplicación, no solamente su archivo .exe."
