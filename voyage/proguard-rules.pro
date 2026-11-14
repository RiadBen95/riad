# Le code de l'application est conservé (noms de classes, champs et méthodes) : il est public
# de toute façon (dépôt ouvert) et cela évite les casses à l'exécution (JSON, enums, WorkManager).
# R8 réduit et optimise seulement les bibliothèques.
-keep class com.riad.voyage.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Bibliothèque Claude (Anthropic) et Jackson : lecture des réponses par réflexion.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.anthropic.**
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
