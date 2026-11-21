# BC registers algorithms and loads implementations by class-name strings.
-keep class org.bouncycastle.** { *; }
# BC's optional LDAP stores use JNDI, absent on Android; this app uses bundled roots only.
-dontwarn javax.naming.**
