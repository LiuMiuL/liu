# Fold Smart Desk R8 규칙
# kotlinx.serialization: 백업 JSON 직렬화에 쓰는 @Serializable 클래스와 생성된 serializer를 보존한다.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class com.miu.fold.dashboard.** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.miu.fold.dashboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.miu.fold.dashboard.**$$serializer { *; }
# enum.valueOf(문자열)로 DB 값을 복원하므로 enum 이름을 보존한다.
-keepclassmembers enum com.miu.fold.dashboard.** { *; }
