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
}

/**
 * A test input to the free-form function.
 *
 * @param data a String input.
 */
@AppFunctionSerializable open class InputToFakeFreeFormFunction(open val data: String)

/**
 * A child class of [InputToFakeFreeFormFunction].
 *
 * @param data a String input.
 */
@AppFunctionSerializable
open class DerivedInputToFakeFreeFormFunction(override val data: String) :
    InputToFakeFreeFormFunction(data)
