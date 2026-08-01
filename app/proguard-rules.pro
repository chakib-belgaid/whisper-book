-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.bouncycastle.**
-dontwarn javax.activation.**
-dontwarn com.gemalto.jp2.JP2Decoder
