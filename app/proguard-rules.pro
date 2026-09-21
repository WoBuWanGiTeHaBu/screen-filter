# 悬浮窗滤镜本身不反射、不序列化，保持默认即可。
# 如果后续开启 R8，注意别裁掉 Service / TileService（已在 manifest 中引用，默认会保留）。
-keepattributes SourceFile,LineNumberTable
