package androidx.compose.runtime

@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.TYPE, AnnotationTarget.EXPRESSION
)
@Retention(AnnotationRetention.SOURCE)
annotation class NoriaOnly