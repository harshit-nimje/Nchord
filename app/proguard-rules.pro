# Nchord — Production R8 / ProGuard Rules

# Preserve source line numbers for actionable crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Retrofit 2: keep service interfaces and generic parameter/return signatures
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**

# Moshi KSP Codegen: preserve @JsonClass DTOs and generated *JsonAdapter constructors
-keep class com.squareup.moshi.JsonAdapter
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

# Room Database: preserve @Entity models and generated *_Impl database/DAO classes
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase {
    void <init>();
}
-keep class com.example.data.local.**_Impl { *; }

# OkHttp 4 / Platform TLS
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
