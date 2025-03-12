# TODO

1. Handle permissions in case of denial, or hibernation
   2. https://developer.android.com/topic/performance/app-hibernation
3. Notification best practices
   4. https://developer.android.com/develop/ui/views/notifications/notification-permission#best-practices
5. Photo picker
   6. https://developer.android.com/training/data-storage/shared/photopicker
7. edge-to-edge?
   8. https://developer.android.com/develop/ui/compose/layouts/insets
9. revive leak canary?

# Probably unnecessary migrations

1. 31
   1. https://developer.android.com/develop/background-work/services/fgs#background-start-restrictions
   2. https://developer.android.com/about/versions/12/behavior-changes-12#notification-trampolines
3. 33
   4. https://developer.android.com/topic/performance/background-optimization#bg-restrict
5. 34
   6. Strangely this is reported in gradle cache for work and room
      7. https://developer.android.com/about/versions/14/changes/fgs-types-required
8. In some cases, the addition of the java.lang.ClassValue class causes an issue if you try to shrink, obfuscate, and optimize your app using ProGuard. If your app was developed against an older version of the runtime without the java.lang.ClassValue class available, then these optimizations might remove the computeValue method from classes derived from java.lang.ClassValue.