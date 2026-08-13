@ECHO OFF
@REM Apache Maven Wrapper startup batch script (classic form)

SET ERROR_CODE=0

IF NOT "%MAVEN_SKIP_RC%" == "" GOTO skipRcPre
IF EXIST "%HOME%\mavenrc_pre.bat" CALL "%HOME%\mavenrc_pre.bat"
:skipRcPre

SET MAVEN_PROJECTBASEDIR=%~dp0
IF NOT "%MAVEN_PROJECTBASEDIR:~-1%" == "\" SET MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR%\

IF NOT "%JAVA_HOME%" == "" GOTO haveJavaHome
SET JAVA_EXE=java.exe
GOTO checkJavaExe

:haveJavaHome
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
IF EXIST "%JAVA_EXE%" GOTO checkJavaExe
ECHO JAVA_HOME is set but %JAVA_EXE% does not exist
SET ERROR_CODE=1
GOTO error

:checkJavaExe

SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

"%JAVA_EXE%" %JAVA_OPTS% %MAVEN_OPTS% ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  -classpath %WRAPPER_JAR% ^
  %WRAPPER_LAUNCHER% %*
IF ERRORLEVEL 1 GOTO error
GOTO end

:error
SET ERROR_CODE=1

:end
IF NOT "%MAVEN_SKIP_RC%" == "" GOTO skipRcPost
IF EXIST "%HOME%\mavenrc_post.bat" CALL "%HOME%\mavenrc_post.bat"
:skipRcPost

exit /B %ERROR_CODE%
