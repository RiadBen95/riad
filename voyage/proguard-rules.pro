# Le code de l'application est conservé (noms de classes, champs et méthodes) : il est public
# de toute façon (dépôt ouvert) et cela évite les casses à l'exécution (JSON, enums, WorkManager).
# R8 réduit et optimise seulement les bibliothèques.
-keep class com.riad.voyage.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
