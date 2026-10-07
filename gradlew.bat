@echo off
setlocal

set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
set "WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
    echo Bootstrapping Gradle wrapper...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
    if errorlevel 1 (
        echo Failed to download gradle-wrapper.jar.
        exit /b 1
    )
)

if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% ^
  "-Dorg.gradle.appname=gradlew" ^
  -classpath "%WRAPPER_JAR%" ^
  org.gradle.wrapper.GradleWrapperMain %*
if errorlevel 1 exit /b %errorlevel%

endlocal
