# converter.py calls ProgressListener.onProgress reflectively, so R8 must not rename/remove it.
-keep interface com.softtunes.player.ProgressListener { *; }
-keep class * implements com.softtunes.player.ProgressListener { *; }
