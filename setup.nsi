; For unicode file should be in UTF8 format WITH BOM
Unicode true

!include "MUI2.nsh"
!include "FileFunc.nsh"
!insertmacro RefreshShellIcons
!insertmacro un.RefreshShellIcons

;-------------------------------
!define HOME "E:\AudioCataloger"

!define MUI_ICON "${HOME}\images.nsis\icon_setup.ico"
!define MUI_UNICON "${HOME}\images.nsis\uninstall.ico"

!define MUI_HEADERIMAGE_UNBITMAP_RTL_STRETCH FitControl
!define MUI_HEADERIMAGE_BITMAP_RTL_STRETCH FitControl

!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP_RTL "${HOME}\images.nsis\logo.bmp"
!define MUI_HEADERIMAGE_UNBITMAP_RTL "${HOME}\images.nsis\logo.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${HOME}\images.nsis\welcome.bmp"

!define PROGRAM_NAME "Maknoon Audio Cataloger"
!define PROGRAM_NAME_AR "مفهرس المحاضرات"

OutFile "AudioCatalogerVMx64.exe"

RequestExecutionLevel admin			; To avoid shortcut removal problem [http://nsis.sourceforge.net/Shortcuts_removal_fails_on_Windows_Vista]

; The default installation directory
InstallDir "$PROGRAMFILES64\${PROGRAM_NAME}"

; Registry key to check for directory (so if you install again, it will overwrite the old one automatically)
InstallDirRegKey HKLM "SOFTWARE\${PROGRAM_NAME}" "Install_Dir"

;--------------------------------
;Pages

!define MUI_WELCOMEPAGE_TITLE "برنامج المفهرس لمسموعات جمع من أهل العلم"
!define MUI_WELCOMEPAGE_TEXT "السلام عليكم ورحمة الله وبركاته$\r$\n\
$\r$\n\
يتميز البرنامج بأسلوب بحث جديد ومطور باستخدام الفهارس. ومن مميزاته خاصية التحديث التلقائي لقاعدة البيانات وإمكانية صنع فهارس لأشرطة أهل العلم وتصديرها.$\r$\n\
$\r$\n\
يمنع بيع البرنامج أو استخدامه فيما يخالف أهل السنة والجماعة$\r$\n\
البرنامج مصرح لنشره واستخدامه من جميع المسلمين$\r$\n\
جميع الحقوق محفوظة لموقع مكنون$\r$\n\
الإصدار 4.6"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES

;!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

;--------------------------------
!insertmacro MUI_LANGUAGE "Arabic"
;--------------------------------

Function .onInit
	
	ReadRegStr $R0 HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "UninstallString"
	StrCmp $R0 "" done
	
	MessageBox MB_YESNOCANCEL|MB_ICONINFORMATION "هناك نسخة أخرى من برنامج مفهرس المحاضرات على جهازك. هل تريد إزالتها؟" IDYES uninst IDCANCEL abort
	Goto done
	
	;Run the uninstaller
	uninst:
		ClearErrors
		ExecWait '$R0 _?=$INSTDIR' ;Do not copy the uninstaller to a temp file
		
		IfErrors no_remove_uninstaller
			;You can either use Delete /REBOOTOK in the uninstaller or add some code
			;here to remove the uninstaller. Use a registry key to check
			;whether the user has chosen to uninstall. If you are using an uninstaller
			;components page, make sure all sections are uninstalled.
		no_remove_uninstaller:
		Goto done
	
	abort:
		Abort
	
	done:
	
FunctionEnd

; The name of the installer
Name "${PROGRAM_NAME_AR}"
BrandingText "${PROGRAM_NAME_AR}"
VIProductVersion "4.6.0.0"
VIAddVersionKey "ProductName" "${PROGRAM_NAME_AR}"
VIAddVersionKey "CompanyName" "برامج مكنون"
VIAddVersionKey "LegalCopyright" "©maknoon.com"
VIAddVersionKey "FileDescription" "${PROGRAM_NAME_AR}"
VIAddVersionKey "FileVersion" "4.6"
VIAddVersionKey "InternalName" "${PROGRAM_NAME}"

Section "${PROGRAM_NAME}" SEC_IDX

	SectionIn RO
	
	SetOutPath "$INSTDIR\jdk"
	File /r jdk\*.*
	
	SetOutPath "$INSTDIR\resources"
	File /r resources\*.*
	
	SetOutPath "$INSTDIR\com"
	File /r com\*.*
	
	SetOutPath "$INSTDIR"
	File "AudioCataloger.jar"
	File "AudioCataloger.exe"
	File "AudioCataloger.l4j.ini"
	File "startup.bat"
	File "DBCheck.bat"
	File "DBCompact.bat"
	File "DBEmpty.bat"
	File "icon.ico"
	
	; Write the installation path into the registry
	WriteRegStr HKLM "SOFTWARE\${PROGRAM_NAME}" "Install_Dir" "$INSTDIR"

	; Write the uninstall keys for Windows
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "DisplayName" "${PROGRAM_NAME_AR}"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "URLInfoAbout" "https://www.maknoon.com"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "DisplayIcon" "$INSTDIR\icon.ico"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "DisplayVersion" "4.6"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "Publisher" "برامج مكنون"
	WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "NoModify" 1
	WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "NoRepair" 1
	WriteUninstaller "$INSTDIR\uninstall.exe"
	
	; Slow
	;${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
	;IntFmt $0 "0x%08X" $0
	
	; http://nsis.sourceforge.net/Add_uninstall_information_to_Add/Remove_Programs
	SectionGetSize ${SEC_IDX} $0
	IntFmt $0 "0x%08X" $0
	WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}" "EstimatedSize" "$0"
	
	; To allow all users to save setting/updates in Vista/W7 because of UAC
	AccessControl::GrantOnFile "$INSTDIR" "(BU)" "FullAccess"
	
SectionEnd

; Optional section (can be disabled by the user)
Section "Start Menu Shortcuts"

	CreateShortCut "$SMPROGRAMS\${PROGRAM_NAME_AR}.lnk" "$INSTDIR\jdk\bin\javaw" "-Dfile.encoding=UTF-8 -Xms512m -Xmx1024m -Dprism.lcdtext=false --module-path resources\mods --add-modules javafx.controls,javafx.graphics,javafx.media -jar $\"$INSTDIR\AudioCataloger.jar$\"" "$INSTDIR\icon.ico"
	
	; Create desktop shortcut
	CreateShortCut "$DESKTOP\${PROGRAM_NAME_AR}.lnk" "$INSTDIR\jdk\bin\javaw" "-Dfile.encoding=UTF-8 -Xms512m -Xmx1024m -Dprism.lcdtext=false --module-path resources\mods --add-modules javafx.controls,javafx.graphics,javafx.media -jar $\"$INSTDIR\AudioCataloger.jar$\"" "$INSTDIR\icon.ico"

SectionEnd

Section "Uninstall"

	; Remove registry keys
	DeleteRegKey HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROGRAM_NAME}"
	DeleteRegKey HKLM "SOFTWARE\${PROGRAM_NAME}"

	; Remove shortcut used
	Delete "$DESKTOP\${PROGRAM_NAME_AR}.lnk"
	Delete "$SMPROGRAMS\${PROGRAM_NAME_AR}.lnk"
	RMDir /r "$INSTDIR"
	
SectionEnd

;---------------------
;Uninstaller Functions

Function un.onInit
FunctionEnd