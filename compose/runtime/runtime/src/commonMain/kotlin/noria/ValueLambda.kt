package noria

/**
 * When processed by the noria-compiler-plugin, function expressions (i.e., lambdas) with this annotation will receive value semantics
 * (also known as "structural equality" or "value equality") instead of the default which is reference (identity) equality.
 *
 * This is achieved by capturing the lambda closure and storing it in an instance of [noria.impl.Closure], along with the actual
 * lambda instance.
 *
 * This transformation is automatically applied to all lambdas that are declared in `@Composable` function bodies, with or without this
 * annotation. For any lambdas outside of `@Composable` function bodies, this annotation can be manually applied to achieve the same
 * behavior.
 *
 * This can be useful, for example, to stabilize lambdas that would otherwise get reallocated on every recomposition, and which aren't
 * automatically stabilized by a surrounding `@Composable` function block:
 * ```
 * fun Modifier.someModifierInvolvingALambda(someCallback: () -> Unit): Modifier {
 *   return this.then(
 *     SomeModifierInvolvingALambda @ValueLambda {
 *       someCallback()
 *     }
 *   )
 * }
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class ValueLambda
