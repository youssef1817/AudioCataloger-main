SET HOME=E:\AudioCataloger\resources\bin\vlc
SET PLUG=E:\AudioCataloger\resources\bin\vlc\plugins

cd %HOME%

@RD /S /Q hrtfs locale lua msi skins

del AUTHORS.txt COPYING.txt NEWS.txt README.txt THANKS.txt vlc.exe vlc.ico vlc-cache-gen.exe
@RD /S /Q sdk

REM TODO: working but to be Checked with other formats e.g. mp3, wma
del axvlc.dll npvlc.dll

cd %PLUG%
@RD /S /Q control d3d9 d3d11 demux gui keystore lua packetizer services_discovery stream_extractor stream_filter stream_out text_renderer visualization nvdec video_chroma video_splitter

cd %PLUG%\access
del libaccess_concat_plugin.dll libaccess_imem_plugin.dll libaccess_srt_plugin.dll libaccess_wasapi_plugin.dll libbluray-awt-j2se-1.2.1.jar libbluray-j2se-1.2.1.jar libdcp_plugin.dll libnfs_plugin.dll liblibbluray_plugin.dll libimem_plugin.dll libvnc_plugin.dll libsftp_plugin.dll

cd %PLUG%\misc
del libmedialibrary_plugin.dll libxml_plugin.dll

cd %PLUG%\access_output
del libaccess_output_srt_plugin.dll 

cd %PLUG%\audio_filter
del libspatialaudio_plugin.dll

cd %PLUG%\meta_engine
del libtaglib_plugin.dll

cd %PLUG%\codec
del liba52_plugin.dll libadpcm_plugin.dll libaes3_plugin.dll libaom_plugin.dll libaraw_plugin.dll libaribsub_plugin.dll libcc_plugin.dll libcdg_plugin.dll libcrystalhd_plugin.dll libcvdsub_plugin.dll libd3d11va_plugin.dll libdav1d_plugin.dll libdca_plugin.dll libddummy_plugin.dll libdmo_plugin.dll libdvbsub_plugin.dll libdxva2_plugin.dll libedummy_plugin.dll libfaad_plugin.dll libflac_plugin.dll libfluidsynth_plugin.dll libg711_plugin.dll libjpeg_plugin.dll libkate_plugin.dll liblibass_plugin.dll liblpcm_plugin.dll libmft_plugin.dll libmpg123_plugin.dll liboggspots_plugin.dll libpng_plugin.dll libqsv_plugin.dll librawvideo_plugin.dll librtpvideo_plugin.dll libschroedinger_plugin.dll libscte18_plugin.dll libscte27_plugin.dll libsdl_image_plugin.dll libspdif_plugin.dll libspeex_plugin.dll libspudec_plugin.dll libstl_plugin.dll libsubsdec_plugin.dll libsubstx3g_plugin.dll libsubsusf_plugin.dll libsvcdsub_plugin.dll libt140_plugin.dll libtextst_plugin.dll libtheora_plugin.dll libttml_plugin.dll libtwolame_plugin.dll libuleaddvaudio_plugin.dll libvorbis_plugin.dll libvpx_plugin.dll libwebvtt_plugin.dll libx26410b_plugin.dll libx264_plugin.dll libx265_plugin.dll libzvbi_plugin.dll librav1e_plugin.dll libnvdec_plugin.dll
pause