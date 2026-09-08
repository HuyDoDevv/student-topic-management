@echo off
setlocal DisableDelayedExpansion
cd /d "%~dp0"

if not exist ".env" (
    echo Chua co .env. Chay: copy .env.example .env roi sua cau hinh. 1>&2
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"

if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

call mvnw.cmd spring-boot:run %*
exit /b %ERRORLEVEL%
