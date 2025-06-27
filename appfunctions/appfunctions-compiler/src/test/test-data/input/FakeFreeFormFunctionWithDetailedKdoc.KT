package com.testdata

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction

class FakeFreeFormFunctions {
    /**
     * A fake free-form AppFunction to test the description element in the function's generated
     * metadata xml.
     *
     * This kdoc consists of multiple paragraphs and is used as the function's description to the
     * agent.
     *
     * @param appFunctionContext the AppFunction's context.
     * @param input an input AppFunctionSerializable.
     * @return a string.
     * @throws IllegalArgumentException if the inputString is in an incorrect format.
     */
    @AppFunction(isDescribedByKdoc = true)
    fun fakeFreeFormFunction_isDescribedByKdoc_true(
        appFunctionContext: AppFunctionContext,
        input: InputToFakeFreeFormFunction,
    ): String {
        return input.data
    }

    /**
     * A fake free-form AppFunction to test the description elements in the function generated
     * metadata xml. This version uses a derived serializable class.
     *
     * This kdoc consists of multiple paragraphs and is used as the function's description to the
     * agent.
     *
     * @param appFunctionContext the AppFunction's context.
     * @param input an input AppFunctionSerializable.
     * @return a string.
     * @throws IllegalArgumentException if the inputString is in an incorrect format.
     */
    @AppFunction(isDescribedByKdoc = true)
    fun fakeFreeFormFunction_isDescribedByKdoc_withDerivedSerializable_true(
        appFunctionContext: AppFunctionContext,
        input: DerivedInputToFakeFreeFormFunction,
    ): String {
        return input.data
    }

    /**
     * A fake free-form AppFunction to test the description element in the function's generated
     * metadata xml.
     *
     * This kdoc will not be used as the description since `isDescribedByKdoc` is set to false.
     *
     * @param appFunctionContext the AppFunction's context.
     * @param input an input AppFunctionSerializable.
     * @return a string.
     * @throws IllegalArgumentException if the inputString is in an incorrect format.
     */
    @AppFunction(isDescribedByKdoc = false)
    fun fakeFreeFormFunction_isDescribedByKdoc_false(
        appFunctionContext: AppFunctionContext,
        input: InputToFakeFreeFormFunction,
    ): String {
        return input.data
    }

    /** Tests the description elements for the input serializable's properties. */
    @AppFunction(isDescribedByKdoc = true)
    fun fakeFreeFormFunction_withParametrizedTypes(
        appFunctionContext: AppFunctionContext,
        parametrizedTypesInput: InputWithParametrizedTypes,
    ) {}
}

/** A test input to the free-form function. */
@AppFunctionSerializable
open class InputToFakeFreeFormFunction(
    /** A string input. */
    open val data: String,
)

/** A child class of [InputToFakeFreeFormFunction]. */
@AppFunctionSerializable
open class DerivedInputToFakeFreeFormFunction(
    /** A derived string input. */
    override val data: String,
    /** An int input. */
    open val anotherData: Int,
) : InputToFakeFreeFormFunction(data)

/** An input class with parametrized types */
@AppFunctionSerializable
open class InputWithParametrizedTypes(
    /** Example parametrized primitive. */
    val parametrizedInt: Optional<Int>,
    /** Example parametrized nullable primitive. */
    val parametrizedNullableInt: Optional<Int?>,
    /** Example parametrized entity. */
    val parametrizedSerializable: Optional<InputToFakeFreeFormFunction>,
    /** Example parametrized nullable entity. */
    val parametrizedNullableSerializable: Optional<InputToFakeFreeFormFunction?>,
)

/** A generic serializable. */
@AppFunctionSerializable data class Optional<T>(
    /** The value. */
    val value: T
)
