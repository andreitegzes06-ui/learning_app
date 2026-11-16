param(
    [ValidateSet('build', 'run', 'ui-test')][string]$Task = 'run',
    [switch]$IncludeUiTests,
    [switch]$Demo,
    [switch]$RealDemoTest,
    [switch]$WindowsBlockerTests,
    [ValidateSet('simulated','windows-dry-run','windows')][string]$Blocker = 'simulated',
    [string]$DataDirectory,
    [ValidateSet('dashboard','focus','history','classrooms','workspaces','blocked','goals','statistics','achievements','settings')]
    [string]$StartPage = 'dashboard'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if (-not $env:JAVA_HOME) {
        $localJdk = Get-ChildItem -LiteralPath '.tools/liberica' -Directory -Filter 'jdk-21*-full' -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $localJdk) { $localJdk = Get-ChildItem -LiteralPath '.tools' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1 }
        if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        throw 'Install JDK 21 and set JAVA_HOME to its directory. See README.md.'
    }
    $env:MAVEN_USER_HOME = Join-Path $projectRoot '.tools/wrapper-cache'
    $mavenArguments = @('--batch-mode', '--no-transfer-progress', '-Dmaven.repo.local=.tools/m2')
    if ($IncludeUiTests) { $mavenArguments += '-Dstudymode.ui.tests=true' }
    if ($RealDemoTest) { $mavenArguments += '-Dstudymode.real.demo=true' }
    if ($WindowsBlockerTests) { $mavenArguments += '-Dstudymode.windows.tests=true' }
    switch ($Task) {
        'build' {
            # Native removal handles read-only OneDrive directory attributes which Maven clean cannot remove here.
            $generatedOutput = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'target'))
            if ($generatedOutput -ne (Join-Path $projectRoot 'target')) { throw 'Unexpected generated output path.' }
            if (Test-Path -LiteralPath $generatedOutput) {
                $outputItem = Get-Item -LiteralPath $generatedOutput -Force
                if ($outputItem.LinkType) { throw 'Refusing to remove a linked target directory. Choose a normal local build folder.' }
                Remove-Item -LiteralPath $generatedOutput -Recurse -Force
            }
            $mavenArguments += @('clean', 'verify')
        }
        'ui-test' { $mavenArguments += @('-Dstudymode.ui.tests=true', 'verify') }
        'run' {
            $launchOptions = '--start-page=' + $StartPage
            $launchOptions += ' --blocker=' + $Blocker
            if ($Demo) { $launchOptions += ' --demo=true' }
            if ($DataDirectory) {
                $absoluteData = [System.IO.Path]::GetFullPath($DataDirectory)
                if ($absoluteData.Contains('"')) { throw 'Data directory cannot contain a quote.' }
                $launchOptions += ' --data-dir="' + $absoluteData + '"'
            }
            $mavenArguments += @('javafx:run', ('-Djavafx.args=' + $launchOptions))
        }
    }
    & .\mvnw.cmd @mavenArguments
    $buildExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $buildExitCode
