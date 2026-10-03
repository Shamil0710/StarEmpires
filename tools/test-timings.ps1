param(
    [string]$ReportsDirectory = 'target/surefire-reports',
    [string]$OutputDirectory = 'target/test-timings',
    [int]$Top = 20
)

$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$reports = @(Get-ChildItem -LiteralPath $ReportsDirectory -Filter 'TEST-*.xml')
if ($reports.Count -eq 0) { throw "No Surefire XML reports in $ReportsDirectory" }

$classes = @()
$methods = @()
foreach ($file in $reports) {
    [xml]$report = [IO.File]::ReadAllText($file.FullName)
    $suite = $report.testsuite
    $classes += [pscustomobject]@{
        Class = [string]$suite.name
        Seconds = [double]::Parse($suite.time, $culture)
        Tests = [int]$suite.tests
        Skipped = [int]$suite.skipped
        Failures = [int]$suite.failures
        Errors = [int]$suite.errors
        ReportUpdatedUtc = $file.LastWriteTimeUtc.ToString('o')
    }
    foreach ($case in $suite.testcase) {
        $methods += [pscustomobject]@{
            Class = [string]$case.classname
            Method = [string]$case.name
            Seconds = if ($case.time) { [double]::Parse($case.time, $culture) } else { 0.0 }
        }
    }
}

$classes = @($classes | Sort-Object Seconds -Descending)
$methods = @($methods | Sort-Object Seconds -Descending)
$total = ($classes | Measure-Object Seconds -Sum).Sum
$tests = ($classes | Measure-Object Tests -Sum).Sum
$skipped = ($classes | Measure-Object Skipped -Sum).Sum
$failures = ($classes | Measure-Object Failures -Sum).Sum
$errors = ($classes | Measure-Object Errors -Sum).Sum
$null = New-Item -ItemType Directory -Force -Path $OutputDirectory
$classes | Select-Object Class, @{Name='Seconds'; Expression={$_.Seconds.ToString('F6', $culture)}},
    Tests, Skipped, Failures, Errors, ReportUpdatedUtc |
    Export-Csv -LiteralPath (Join-Path $OutputDirectory 'classes.csv') -NoTypeInformation -Encoding UTF8
$methods | Select-Object Class, Method, @{Name='Seconds'; Expression={$_.Seconds.ToString('F6', $culture)}} |
    Export-Csv -LiteralPath (Join-Path $OutputDirectory 'methods.csv') -NoTypeInformation -Encoding UTF8
$lines = @(
    '# Test timings',
    '',
    "$($classes.Count) classes; $tests tests; $skipped skipped; $failures failures; $errors errors.",
    ('Summed class time: {0} seconds. This is not wall-clock duration when forks run concurrently.' -f $total.ToString('F3', $culture)),
    '',
    'Reports may include older runs after a targeted invocation. Use reports from clean verify for a complete baseline.',
    '',
    '| Class | Seconds | Tests |',
    '| --- | ---: | ---: |'
)
foreach ($row in ($classes | Select-Object -First $Top)) {
    $lines += '| {0} | {1} | {2} |' -f $row.Class, $row.Seconds.ToString('F3', $culture), $row.Tests
}
$lines | Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary.md') -Encoding UTF8
$lines
