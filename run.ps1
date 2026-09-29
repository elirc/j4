param([switch]$Test, [string]$DataPath)
$ErrorActionPreference = 'Stop'
$compiler = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$runtime = Join-Path (Split-Path $compiler) 'java.exe'
$projectPath = $PSScriptRoot
$buildPath = Join-Path $projectPath 'out'
New-Item -ItemType Directory -Force -Path $buildPath | Out-Null
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'src/quiz') -Filter '*.java' | ForEach-Object FullName)
if ($Test) { $sourceFiles += @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'test/quiz') -Filter '*.java' | ForEach-Object FullName) }
& $compiler --release 17 -Xlint:all -d $buildPath @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
if ($Test) { & $runtime -cp $buildPath quiz.QuestionBankTest } else {
  if (-not $DataPath) { $DataPath = Join-Path $projectPath 'data/questions.txt' }
  & $runtime -cp $buildPath quiz.App $DataPath
}
if ($LASTEXITCODE -ne 0) { throw 'Java process failed.' }
