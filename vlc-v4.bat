SET HOME=E:\AudioCataloger\resources\bin\vlc
SET PLUG=E:\AudioCataloger\resources\bin\vlc\plugins

cd %HOME%

@RD /S /Q hrtfs locale lua msi skins sdk languages

del AUTHORS.txt COPYING.txt NEWS.txt README.txt THANKS.txt vlc.exe vlc.ico vlc-cache-gen.exe

del axvlc.dll npvlc.dll

cd %PLUG%
@RD /S /Q control d3d9 d3d11 gui keystore logger lua services_discovery spu stream_extractor stream_filter stream_out text_renderer video_chroma video_filter video_output video_splitter visualization

cd %PLUG%\packetizer
del libpacketizer_av1_plugin.dll libpacketizer_copy_plugin.dll libpacketizer_dirac_plugin.dll libpacketizer_dts_plugin.dll libpacketizer_flac_plugin.dll libpacketizer_h264_plugin.dll libpacketizer_hevc_plugin.dll libpacketizer_mlp_plugin.dll libpacketizer_mpeg4audio_plugin.dll libpacketizer_mpeg4video_plugin.dll libpacketizer_mpegvideo_plugin.dll libpacketizer_vc1_plugin.dll libpacketizer_a52_plugin.dll

cd %PLUG%\demux
del libh26x_plugin.dll librawdv_plugin.dll libimage_plugin.dll librawvid_plugin.dll libadaptive_plugin.dll libmjpeg_plugin.dll libaiff_plugin.dll libmkv_plugin.dll libsid_plugin.dll libasf_plugin.dll libmod_plugin.dll libsmf_plugin.dll libau_plugin.dll libmp4_plugin.dll libsubtitle_plugin.dll libavi_plugin.dll libmpc_plugin.dll libts_plugin.dll libcaf_plugin.dll libmpgv_plugin.dll libtta_plugin.dll libdemuxdump_plugin.dll libnoseek_plugin.dll libty_plugin.dll libdemux_cdg_plugin.dll libnsc_plugin.dll libvc1_plugin.dll libdemux_chromecast_plugin.dll libnsv_plugin.dll libvobsub_plugin.dll libdemux_stl_plugin.dll libnuv_plugin.dll libvoc_plugin.dll libdiracsys_plugin.dll libogg_plugin.dll libwav_plugin.dll libdirectory_demux_plugin.dll libplaylist_plugin.dll libxa_plugin.dll libps_plugin.dll libflacsys_plugin.dll libpva_plugin.dll libgme_plugin.dll librawaud_plugin.dll

cd %PLUG%\access
del libaccess_concat_plugin.dll libaccess_imem_plugin.dll libaccess_srt_plugin.dll libaccess_wasapi_plugin.dll libbluray-awt-j2se-1.2.0.jar libbluray-j2se-1.2.0.jar libdcp_plugin.dll libnfs_plugin.dll libsatip_plugin.dll liblibbluray_plugin.dll libimem_plugin.dll

cd %PLUG%\access_output
del libaccess_output_srt_plugin.dll 

cd %PLUG%\audio_filter
del libspatialaudio_plugin.dll

cd %PLUG%\codec
del liba52_plugin.dll libadpcm_plugin.dll libaes3_plugin.dll libaom_plugin.dll libaraw_plugin.dll libaribsub_plugin.dll libcc_plugin.dll libcdg_plugin.dll libcrystalhd_plugin.dll libcvdsub_plugin.dll libd3d11va_plugin.dll libdav1d_plugin.dll libdca_plugin.dll libddummy_plugin.dll libdmo_plugin.dll libdvbsub_plugin.dll libdxva2_plugin.dll libedummy_plugin.dll libfaad_plugin.dll libflac_plugin.dll libfluidsynth_plugin.dll libg711_plugin.dll libjpeg_plugin.dll libkate_plugin.dll liblibass_plugin.dll liblpcm_plugin.dll libmft_plugin.dll libmpg123_plugin.dll liboggspots_plugin.dll libpng_plugin.dll libqsv_plugin.dll librawvideo_plugin.dll librtpvideo_plugin.dll libschroedinger_plugin.dll libscte18_plugin.dll libscte27_plugin.dll libsdl_image_plugin.dll libspdif_plugin.dll libspeex_plugin.dll libspudec_plugin.dll libstl_plugin.dll libsubsdec_plugin.dll libsubstx3g_plugin.dll libsubsusf_plugin.dll libsvcdsub_plugin.dll libt140_plugin.dll libtextst_plugin.dll libtheora_plugin.dll libttml_plugin.dll libtwolame_plugin.dll libuleaddvaudio_plugin.dll libvorbis_plugin.dll libvpx_plugin.dll libwebvtt_plugin.dll libx26410b_plugin.dll libx264_plugin.dll libx265_plugin.dll libzvbi_plugin.dll
pause