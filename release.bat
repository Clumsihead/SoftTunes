@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title SoftTunes - push and build release

where git >nul 2>nul
if errorlevel 1 (
  echo Git is not installed. Get it from https://git-scm.com/download/win and run this again.
  pause
  exit /b 1
)

rem ---- first-time setup: make this folder a repo and connect it to GitHub ----
if not exist ".git" (
  echo This folder is not a Git repository yet.
  set /p REMOTE=Paste your GitHub repo URL, like https://github.com/you/SoftTunes.git : 
  if "!REMOTE!"=="" (
    echo No URL given.
    pause
    exit /b 1
  )
  git init -b main
  git remote add origin !REMOTE!
)

rem ---- version tag ----
set "VERSION=%~1"
if "%VERSION%"=="" set /p VERSION=Version for this release, like 1.0.0 or v1.0.0 : 
if "%VERSION%"=="" (
  echo No version given.
  pause
  exit /b 1
)
if /i not "%VERSION:~0,1%"=="v" set "VERSION=v%VERSION%"

git rev-parse -q --verify "refs/tags/%VERSION%" >nul 2>nul
if not errorlevel 1 (
  echo The tag %VERSION% already exists. Pick a new version number.
  pause
  exit /b 1
)

rem ---- stop tracking build junk if it was committed before, then commit everything ----
git rm -r -q --cached --ignore-unmatch .gradle build app/build .idea local.properties >nul 2>nul
git add -A
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "Release %VERSION%"
  if errorlevel 1 (
    echo Commit failed. If Git asks who you are, run:
    echo   git config --global user.name "Your Name"
    echo   git config --global user.email "you@example.com"
    pause
    exit /b 1
  )
) else (
  echo No file changes to commit, continuing with the tag only.
)

for /f "delims=" %%b in ('git branch --show-current') do set "BRANCH=%%b"
if "%BRANCH%"=="" set "BRANCH=main"

echo.
echo Pushing %BRANCH% ...
git push -u origin %BRANCH%
if errorlevel 1 (
  echo.
  echo Push failed. If GitHub says the remote has changes you do not have, run:
  echo   git pull --rebase origin %BRANCH%
  echo and then run this file again.
  pause
  exit /b 1
)

echo Tagging %VERSION% - this starts the GitHub build ...
git tag %VERSION%
git push origin %VERSION%
if errorlevel 1 (
  echo Could not push the tag.
  pause
  exit /b 1
)

for /f "delims=" %%u in ('git remote get-url origin') do set "URL=%%u"
set "URL=!URL:.git=!"
echo.
echo Done. GitHub is building the APK now, which takes a few minutes.
echo   Watch the build : !URL!/actions
echo   Draft release   : !URL!/releases
echo Open the draft, check it, then press Publish release.
echo.
pause
endlocal
