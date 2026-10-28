# Starts the local theme studio (community/server.py, Python 3.10+ on PATH) and opens the PC editor.
$ErrorActionPreference='Stop'
$studioRoot=Split-Path -Parent $PSScriptRoot
$studioPython=(Get-Command python -ErrorAction SilentlyContinue).Source
if(-not $studioPython){throw 'Python 3.10+ is required (python on PATH).'}
$studioListener=Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue
if(-not $studioListener){
 Start-Process -FilePath $studioPython -ArgumentList @('community/server.py','--port','8787') -WorkingDirectory $studioRoot -WindowStyle Hidden
 Start-Sleep -Milliseconds 800
}
$studioHealth=Invoke-RestMethod 'http://127.0.0.1:8787/health'
if($studioHealth.mode -ne 'local-test'){throw 'Port 8787 is being used by another service.'}
Start-Process 'http://127.0.0.1:8787/creator/'
