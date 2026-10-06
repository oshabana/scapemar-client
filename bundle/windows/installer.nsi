Unicode true
Name "ScapeMar"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\ScapeMar"
RequestExecutionLevel user
SetCompressor /SOLID lzma
Icon "${ICON}"
UninstallIcon "${ICON}"

Page directory
Page instfiles
UninstPage uninstConfirm
UninstPage instfiles

!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\ScapeMar"

Section "ScapeMar"
  SetOutPath "$INSTDIR"
  RMDir /r "$INSTDIR\runtime"
  File /r "${SOURCE}\*"
  WriteUninstaller "$INSTDIR\Uninstall ScapeMar.exe"
  CreateShortCut "$SMPROGRAMS\ScapeMar.lnk" "$INSTDIR\runtime\bin\javaw.exe" "-cp . ScapeMarLauncher" "$INSTDIR\ScapeMar.ico"
  CreateShortCut "$DESKTOP\ScapeMar.lnk" "$INSTDIR\runtime\bin\javaw.exe" "-cp . ScapeMarLauncher" "$INSTDIR\ScapeMar.ico"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "ScapeMar"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\ScapeMar.ico"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '"$INSTDIR\Uninstall ScapeMar.exe"'
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
SectionEnd

Function .onInstSuccess
  SetOutPath "$INSTDIR"
  Exec '"$INSTDIR\runtime\bin\javaw.exe" -cp . ScapeMarLauncher'
FunctionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\ScapeMar.lnk"
  Delete "$DESKTOP\ScapeMar.lnk"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "${UNINSTALL_KEY}"
SectionEnd
