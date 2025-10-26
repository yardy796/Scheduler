param()

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")

$sources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $repoRoot "src/main/java")
if ($sources.Count -eq 0) {
    Write-Error "No sources found"
    exit 1
}

if (-not (Test-Path (Join-Path $repoRoot "out"))) {
    New-Item -ItemType Directory -Path (Join-Path $repoRoot "out") | Out-Null
}

$javafxLocations = @()

if ($env:JAVAFX_HOME) {
    $candidate = Join-Path $env:JAVAFX_HOME "lib"
    if (Test-Path $candidate) {
        $javafxLocations += (Resolve-Path $candidate).Path
    }
}

$repoCandidate = Join-Path $repoRoot "lib/javafx"
if (Test-Path $repoCandidate) {
    $javafxLocations += (Resolve-Path $repoCandidate).Path
}

$embeddedCandidate = Join-Path $repoRoot "src/main/java/lib/javafx"
if (Test-Path $embeddedCandidate) {
    $javafxLocations += (Resolve-Path $embeddedCandidate).Path
}

if ($javafxLocations.Count -eq 0) {
    Write-Error "JavaFX SDK not found. Set JAVAFX_HOME to the JavaFX SDK root or place the SDK lib folder under lib/javafx or src/main/java/lib/javafx."
    exit 1
}

$modulePath = $javafxLocations -join ';'
$modules = "javafx.controls,javafx.fxml"

javac --release 21 -d (Join-Path $repoRoot "out") --module-path $modulePath --add-modules $modules @($sources.FullName)
