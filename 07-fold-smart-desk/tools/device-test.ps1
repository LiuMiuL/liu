<#
.SYNOPSIS
  Fold Smart Desk: PC 폴더로 가져오기 + 빌드 + 실기기 자동 테스트 + 설치·실행 확인 (Windows PowerShell)

.사용법
  1) 폰: 설정 > 휴대전화 정보 > 소프트웨어 정보 > '빌드번호' 7번 터치 → 개발자 옵션 > USB 디버깅 켜기
  2) 폰을 USB로 PC에 연결하고 폰에 뜨는 'USB 디버깅 허용'을 누른다
  3) PowerShell에서:
       powershell -ExecutionPolicy Bypass -File device-test.ps1
     옵션: -Target "다른\폴더"  -SkipUnitTests  -NoInstall  -Serial <기기ID>

.안전장치
  - ZIP으로 받아 넣은 폴더(07-fold-smart-desk\android가 있음)는 그대로 빌드한다(갱신 없음).
  - 그 밖에 Git 저장소가 아니면서 비어 있지 않은 폴더는 아무것도 덮어쓰지 않고 멈춘다.
  - 자동 테스트는 별도 테스트 앱(com.miu.fold.dashboard.devtest)에서 돈다. 실제 앱 데이터는 건드리지 않는다.
  - 실제 앱 설치는 '업데이트 설치(-r)'만 한다. 서명이 달라 실패해도 자동으로 지우지 않는다.
#>
param(
    [string]$Target = "D:\Miu\Projects\폴드 스마트 대시보드 어플",
    [string]$RepoUrl = "https://github.com/LiuMiuL/liu.git",
    [string]$Branch = "ccr-39e204da-o3u9tv",
    [string]$Serial = "",
    [switch]$SkipUnitTests,
    [switch]$NoInstall
)

# git·java·adb는 정상 진행 메시지도 stderr로 내보낸다. Windows PowerShell 5.1에서 "Stop"이면
# 그 메시지만으로 스크립트가 중단되므로, 외부 명령은 $LASTEXITCODE로 판정하고 기본값은 Continue로 둔다.
$ErrorActionPreference = "Continue"
$Package = "com.miu.fold.dashboard"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Report = New-Object System.Collections.Generic.List[string]
$Mapped = $null

function Step([string]$m) { Write-Host ""; Write-Host "=== $m ===" -ForegroundColor Cyan; $Report.Add(""); $Report.Add("## $m") }
function Ok([string]$m) { Write-Host "  OK  $m" -ForegroundColor Green; $Report.Add("- OK: $m") }
function Warn([string]$m) { Write-Host "  주의 $m" -ForegroundColor Yellow; $Report.Add("- 주의: $m") }
function Fail([string]$m) {
    Write-Host "  실패 $m" -ForegroundColor Red
    $Report.Add("- 실패: $m")
    Save-Report
    if ($Mapped) { Set-Location $env:USERPROFILE; subst $Mapped /D | Out-Null }
    exit 1
}
function Save-Report {
    try {
        $dir = Join-Path $Target "07-fold-smart-desk\docs\device-results"
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        $file = Join-Path $dir "$Stamp.md"
        (@("# 실기기 테스트 결과 $Stamp") + $Report) | Set-Content -Path $file -Encoding UTF8
        Write-Host ""
        Write-Host "결과 보고서: $file" -ForegroundColor Cyan
        Write-Host "이 파일 내용을 Claude에게 붙여넣으면 실패 항목을 이어서 고칠 수 있습니다." -ForegroundColor Cyan
    } catch { Write-Host "보고서 저장 실패: $_" -ForegroundColor Red }
}

# ---------------------------------------------------------------- 1
Step "1. 필요한 도구 확인"
$hasGit = [bool](Get-Command git -ErrorAction SilentlyContinue)
$zipCopy = (Test-Path (Join-Path $Target "07-fold-smart-desk\android\gradlew.bat")) -and -not (Test-Path (Join-Path $Target ".git"))
if (-not $hasGit -and -not $zipCopy) { Fail "git이 없습니다. https://git-scm.com 에서 설치하거나, GitHub에서 ZIP으로 받아 대상 폴더에 풀어 넣으세요." }
if ($hasGit) { Ok "$(git --version)" } else { Ok "git 없음 — ZIP 사본으로 진행" }

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Fail "Android SDK를 찾지 못했습니다($sdk). Android Studio 설치 후 SDK Manager에서 'Android 16 (API 36)'과 Build-Tools 36을 설치하세요." }
Ok "Android SDK: $sdk"

if (-not $env:JAVA_HOME) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr; Ok "JAVA_HOME을 Android Studio 내장 JDK로 설정: $jbr" }
    else { Fail "JDK 21이 필요합니다. Android Studio를 설치하거나 JAVA_HOME을 JDK 21로 지정하세요." }
} else { Ok "JAVA_HOME: $env:JAVA_HOME" }
$jv = (& (Join-Path $env:JAVA_HOME "bin\java.exe") -version 2>&1 | Select-Object -First 1 | Out-String).Trim()
$Report.Add("- Java: $jv")
if ("$jv" -notmatch '"(2[1-9]|[3-9]\d)') { Warn "JDK 21 이상이 아닐 수 있습니다: $jv (빌드가 실패하면 JDK 21을 설치하세요)" }

# ---------------------------------------------------------------- 2
Step "2. 프로젝트를 대상 폴더로 가져오기"
$Report.Add("- 대상 폴더: $Target")
if (Test-Path (Join-Path $Target ".git")) {
    git -C "$Target" fetch origin $Branch
    if ($LASTEXITCODE -ne 0) { Fail "git fetch 실패(네트워크·GitHub 로그인 확인)" }
    $dirty = git -C "$Target" status --porcelain
    if ($dirty) { Warn "대상 폴더에 커밋하지 않은 변경이 있어 최신으로 갱신하지 않고 현재 상태로 진행합니다." }
    else {
        git -C "$Target" checkout $Branch
        git -C "$Target" pull --ff-only origin $Branch
        if ($LASTEXITCODE -ne 0) { Fail "git pull 실패" }
        Ok "기존 저장소를 최신으로 갱신"
    }
} elseif (Test-Path (Join-Path $Target "07-fold-smart-desk\android\gradlew.bat")) {
    # GitHub에서 ZIP으로 받아 풀어 넣은 경우: 내려받기·갱신 없이 그대로 빌드한다.
    Warn "Git 저장소가 아닌 폴더(ZIP으로 받은 것)입니다. 최신으로 갱신하지 않고 들어 있는 파일 그대로 진행합니다. 새 버전은 ZIP을 다시 받아 덮어쓰세요."
} elseif ((Test-Path $Target) -and (Get-ChildItem -Force $Target | Select-Object -First 1)) {
    Fail "대상 폴더가 비어 있지 않고 Git 저장소도 아닙니다. 기존 파일을 지키기 위해 멈춥니다. 빈 폴더를 지정하거나(-Target) 기존 파일을 직접 옮긴 뒤 다시 실행하세요."
} else {
    New-Item -ItemType Directory -Force -Path $Target | Out-Null
    git clone --branch $Branch $RepoUrl "$Target"
    if ($LASTEXITCODE -ne 0) { Fail "git clone 실패. 비공개 저장소면 GitHub 로그인(git credential)이 필요합니다." }
    Ok "저장소를 내려받음"
}
if (Test-Path (Join-Path $Target ".git")) { $Report.Add("- 커밋: $(git -C "$Target" rev-parse --short HEAD)") } else { $Report.Add("- 커밋: (ZIP 사본, Git 정보 없음)") }

# ---------------------------------------------------------------- 3
Step "3. 한글·공백 경로 우회(임시 드라이브 연결)"
foreach ($l in "X","Y","W","V","U","T") {
    if (-not (Test-Path "$($l):\")) { subst "$($l):" "$Target"; if ($LASTEXITCODE -eq 0) { $Mapped = "$($l):"; break } }
}
if (-not $Mapped) { Fail "빈 드라이브 문자를 찾지 못했습니다." }
Ok "$Mapped -> $Target"
$Proj = "$Mapped\07-fold-smart-desk\android"
Set-Location $Proj
"sdk.dir=$($sdk -replace '\\','/')" | Set-Content -Path "local.properties" -Encoding ASCII
Ok "local.properties 작성"

# ---------------------------------------------------------------- 4
Step "4. PC 빌드·단위 테스트"
$tasks = @(":app:assembleDebug", ":app:assembleRelease")
if (-not $SkipUnitTests) { $tasks = @(":app:testDebugUnitTest") + $tasks }
& .\gradlew.bat @tasks --console=plain
if ($LASTEXITCODE -ne 0) { Fail "Gradle 빌드/단위 테스트 실패. 위 로그의 첫 오류를 Claude에게 보여주세요." }
Ok "빌드 성공: $($tasks -join ', ')"

# ---------------------------------------------------------------- 5
Step "5. 연결된 기기 확인"
& $adb start-server | Out-Null
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] }
$unauth = & $adb devices | Where-Object { $_ -match "unauthorized" }
if ($unauth) { Warn "폰 화면에서 'USB 디버깅 허용'을 눌러주세요." }
if (-not $devices) { Fail "연결된 기기가 없습니다. USB 디버깅을 켜고 폰을 연결한 뒤 다시 실행하세요." }
if (-not $Serial) { $Serial = @($devices)[0] }
if (@($devices).Count -gt 1) { Warn "기기가 여러 대입니다. $Serial 사용(-Serial로 지정 가능)" }
$env:ANDROID_SERIAL = $Serial
$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$rel = (& $adb -s $Serial shell getprop ro.build.version.release).Trim()
$sdkInt = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
$size = (& $adb -s $Serial shell wm size).Trim()
$dens = (& $adb -s $Serial shell wm density).Trim()
Ok "기기: $model / Android $rel (API $sdkInt) / $size / $dens"
if ([int]$sdkInt -lt 30) { Fail "이 앱은 Android 11(API 30) 이상이 필요합니다." }

# ---------------------------------------------------------------- 6
Step "6. 실기기 자동 테스트(테스트 전용 앱, 실제 데이터와 분리)"
& .\gradlew.bat :app:connectedDeviceTestAndroidTest --console=plain
$testExit = $LASTEXITCODE
$xml = Get-ChildItem -Recurse -Filter "TEST-*.xml" "app\build\outputs\androidTest-results\connected" -ErrorAction SilentlyContinue
foreach ($f in $xml) {
    [xml]$x = Get-Content $f.FullName -Encoding UTF8
    foreach ($tc in $x.testsuite.testcase) {
        if ($tc.failure) { $Report.Add("- 실패: $($tc.name) — $(($tc.failure.message -split "`n")[0])") }
        else { $Report.Add("- 통과: $($tc.name)") }
    }
}
if ($testExit -ne 0) { Warn "실기기 자동 테스트 중 실패가 있습니다(위 목록)." } else { Ok "실기기 자동 테스트 통과" }

# ---------------------------------------------------------------- 7
if (-not $NoInstall) {
    Step "7. 실제 앱 설치·실행 확인"
    $apk = "app\build\outputs\apk\debug\app-debug.apk"
    $out = & $adb -s $Serial install -r $apk 2>&1
    if ("$out" -match "UPDATE_INCOMPATIBLE") {
        Warn "이미 설치된 앱과 서명이 달라 업데이트할 수 없습니다. 앱 설정 > 백업 내보내기로 자료를 저장한 뒤 직접 삭제하고 다시 실행하세요(스크립트는 자동 삭제하지 않습니다)."
    } elseif ("$out" -notmatch "Success") { Warn "설치 결과: $out" }
    else { Ok "설치(업데이트) 성공" }

    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell am start -W -n "$Package/.MainActivity" | Out-Null
    Start-Sleep -Seconds 4
    $procId = (& $adb -s $Serial shell pidof $Package)
    if ($procId) { Ok "앱 실행 중(pid $procId)" } else { Warn "앱 프로세스를 찾지 못했습니다(실행 직후 종료 가능성)" }
    $crash = & $adb -s $Serial logcat -d -b crash 2>$null | Select-String $Package
    if ($crash) { Warn "크래시 로그 발견:"; $crash | Select-Object -First 30 | ForEach-Object { $Report.Add("    $_") } }
    else { Ok "크래시 로그 없음" }

    $shotDir = Join-Path $Target "07-fold-smart-desk\docs\device-results"
    if (-not (Test-Path $shotDir)) { New-Item -ItemType Directory -Force -Path $shotDir | Out-Null }
    $shot = Join-Path $shotDir "$Stamp-launch.png"
    & $adb -s $Serial shell screencap -p /sdcard/fsd_shot.png
    & $adb -s $Serial pull /sdcard/fsd_shot.png "$shot" | Out-Null
    & $adb -s $Serial shell rm /sdcard/fsd_shot.png
    if (Test-Path $shot) { Ok "화면 캡처: $shot" }
}

# ---------------------------------------------------------------- 8
Step "8. 사람이 직접 확인할 항목(docs/DEVICE_TEST.md)"
$Report.Add("- 접기/펼치기, 회전, 알림(화면 꺼짐), 재부팅, 공유, 백업은 docs/DEVICE_TEST.md 체크리스트로 확인")
Set-Location $Target
subst $Mapped /D | Out-Null
$Mapped = $null
Save-Report
Write-Host ""
Write-Host "완료. 정식(축소) APK: $Target\07-fold-smart-desk\android\app\build\outputs\apk\release\app-release.apk" -ForegroundColor Green
