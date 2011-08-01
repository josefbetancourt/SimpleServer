@rem
@rem File: powerball.bat
@rem Created 12/28/08 J. Betancourt
@rem 
@if "%DEBUG%" == "" @echo off
if "%OS%"=="Windows_NT" setlocal
call setEnv.cmd

pushd ..

groovy src\main\SimpleServer.groovy %*

popd
@rem End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

