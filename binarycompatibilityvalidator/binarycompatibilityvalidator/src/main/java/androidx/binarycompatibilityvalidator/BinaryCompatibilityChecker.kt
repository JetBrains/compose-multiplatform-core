/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // b/407928023
@file:OptIn(ExperimentalLibraryAbiReader::class)

package androidx.binarycompatibilityvalidator

import java.io.File
import org.jetbrains.kotlin.library.abi.AbiClass
import org.jetbrains.kotlin.library.abi.AbiClassifierReference.ClassReference
import org.jetbrains.kotlin.library.abi.AbiClassifierReference.TypeParameterReference
import org.jetbrains.kotlin.library.abi.AbiDeclaration
import org.jetbrains.kotlin.library.abi.AbiDeclarationContainer
import org.jetbrains.kotlin.library.abi.AbiEnumEntry
import org.jetbrains.kotlin.library.abi.AbiFunction
import org.jetbrains.kotlin.library.abi.AbiModality
import org.jetbrains.kotlin.library.abi.AbiProperty
import org.jetbrains.kotlin.library.abi.AbiPropertyKind
import org.jetbrains.kotlin.library.abi.AbiType
import org.jetbrains.kotlin.library.abi.AbiTypeArgument
import org.jetbrains.kotlin.library.abi.AbiTypeArgument.StarProjection
import org.jetbrains.kotlin.library.abi.AbiTypeArgument.TypeProjection
import org.jetbrains.kotlin.library.abi.AbiTypeParameter
import org.jetbrains.kotlin.library.abi.AbiValueParameter
import org.jetbrains.kotlin.library.abi.ExperimentalLibraryAbiReader
import org.jetbrains.kotlin.library.abi.LibraryAbi

@OptIn(ExperimentalLibraryAbiReader::class)
class BinaryCompatibilityChecker(
    private val newLibraryAbi: LibraryAbi,
    private val oldLibraryAbi: LibraryAbi
) {
    private val newLibraryDeclarations by lazy {
        newLibraryAbi.allDeclarations().associateBy { it.asTypeString() }
    }
    private val oldLibraryDeclarations by lazy {
        oldLibraryAbi.allDeclarations().associateBy { it.asTypeString() }
    }

    private fun checkBinariesAreCompatible(
        errors: CompatibilityErrors,
        validate: Boolean
    ): CompatibilityErrors {
        return newLibraryAbi.checkIsBinaryCompatibleWith(oldLibraryAbi, errors, validate)
    }

    private fun LibraryAbi.checkIsBinaryCompatibleWith(
        olderLibraryAbi: LibraryAbi,
        errors: CompatibilityErrors,
        validate: Boolean
    ): CompatibilityErrors {
        topLevelDeclarations.isBinaryCompatibleWith(
            olderLibraryAbi.topLevelDeclarations,
            uniqueName,
            errors
        )
        if (validate && errors.isNotEmpty()) {
            throw ValidationException(errors.toString())
        }
        return errors
    }

    private fun AbiDeclarationContainer.isBinaryCompatibleWith(
        oldContainer: AbiDeclarationContainer,
        parentQualifiedName: String,
        errors: CompatibilityErrors
    ) {
        val isBinaryCompatibleWith:
            AbiDeclaration.(AbiDeclaration, String, CompatibilityErrors) -> Unit =
            { other, /* parentQualifiedName */ _, errs ->
                isBinaryCompatibleWith(other, errs)
            }
        declarations.isBinaryCompatibleWith(
            oldContainer.declarations,
            entityName = "declaration",
            uniqueId = AbiDeclaration::asTypeString,
            isBinaryCompatibleWith = isBinaryCompatibleWith,
            parentQualifiedName = parentQualifiedName,
            errors = errors
        )
    }

    private fun AbiDeclaration.isBinaryCompatibleWith(
        oldDeclaration: AbiDeclaration,
        @Suppress("UNUSED_PARAMETER") parentQualifiedName: String,
        errors: CompatibilityErrors
    ) = isBinaryCompatibleWith(oldDeclaration, errors)

    private fun AbiDeclaration.isBinaryCompatibleWith(
        oldDeclaration: AbiDeclaration,
        errors: CompatibilityErrors
    ) {
        // If we're comparing a class to a function, or any other type, they are not compatible and
        // it's not worth checking anything further. The code that calls this function should
        // generally make sure that doesn't happen, but if it does we fail early.
        if (this::class.java != oldDeclaration::class.java) {
            errors.add(
                "type changed from ${this::class.simpleName} to " +
                    "${oldDeclaration::class.simpleName} for $qualifiedName",
            )
            return
        }
        when (this) {
            is AbiClass -> isBinaryCompatibleWith(oldDeclaration as AbiClass, errors)
            is AbiFunction -> isBinaryCompatibleWith(oldDeclaration as AbiFunction, errors)
            is AbiProperty -> isBinaryCompatibleWith(oldDeclaration as AbiProperty, errors)
            is AbiEnumEntry -> Unit
        }
    }

    private fun AbiClass.isBinaryCompatibleWith(oldClass: AbiClass, errors: CompatibilityErrors) {
        if (modality != oldClass.modality) {
            when {
                modality == AbiModality.OPEN && oldClass.modality == AbiModality.FINAL -> Unit
                modality == AbiModality.OPEN && oldClass.modality == AbiModality.SEALED -> Unit
                modality == AbiModality.OPEN && oldClass.modality == AbiModality.ABSTRACT -> Unit
                modality == AbiModality.ABSTRACT && oldClass.modality == AbiModality.SEALED -> Unit
                else ->
                    errors.add(
                        "modality changed from ${oldClass.modality} to $modality for $qualifiedName"
                    )
            }
        }
        if (kind != oldClass.kind) {
            errors.add("kind changed from ${oldClass.kind} to $kind for $qualifiedName")
        }
        if (isInner != oldClass.isInner) {
            errors.add("isInner changed from ${oldClass.isInner} to $isInner for $qualifiedName")
        }
        if (isValue != oldClass.isValue) {
            errors.add("isValue changed from ${oldClass.isValue} to $isValue for $qualifiedName")
        }
        if (isFunction != oldClass.isFunction) {
            when {
                isFunction && !oldClass.isFunction -> Unit
                else ->
                    errors.add(
                        "isFunction changed from ${oldClass.isFunction} to $isFunction for " +
                            "$qualifiedName"
                    )
            }
        }

        // Check that previous supertypes are still currently supertypes
        allSuperTypes(newLibraryDeclarations)
            .isBinaryCompatibleWith(
                oldClass.allSuperTypes(oldLibraryDeclarations),
                entityName = "superType",
                uniqueId = AbiType::asString,
                isBinaryCompatibleWith = AbiType::isBinaryCompatibleWith,
                parentQualifiedName = qualifiedName.toString(),
                errors = errors
            )
        typeParameters.isBinaryCompatibleWith(
            oldClass.typeParameters,
            entityName = "typeParameter",
            uniqueId = AbiTypeParameter::tag,
            isBinaryCompatibleWith = AbiTypeParameter::isBinaryCompatibleWith,
            parentQualifiedName = qualifiedName.toString(),
            errors = errors,
            isAllowedAddition = { false }
        )
        val newDecs = allDeclarationsIncludingInherited(newLibraryDeclarations)
        val oldDecs = oldClass.allDeclarationsIncludingInherited(oldLibraryDeclarations)
        newDecs.isBinaryCompatibleWith(
            oldDecs,
            entityName = "declaration",
            uniqueId = AbiDeclaration::asUnqualifiedTypeString,
            isBinaryCompatibleWith = { other, parentName, errs ->
                isBinaryCompatibleWith(other, parentName, errs)
            },
            isAllowedAddition = {
                when {
                    this is AbiFunction -> modality != AbiModality.ABSTRACT
                    else -> true
                }
            },
            parentQualifiedName = qualifiedName.toString(),
            errors = errors
        )
    }

    private fun AbiClass.allSuperTypes(declarations: Map<String, AbiDeclaration>): List<AbiType> {
        return superTypes + superTypes.flatMap { it.allSuperTypes(declarations) }
    }

    private fun AbiType.allSuperTypes(declarations: Map<String, AbiDeclaration>): List<AbiType> {
        val abiClass = declarations[asString()] as? AbiClass ?: return emptyList()
        val superTypes = abiClass.superTypes
        return superTypes + superTypes.flatMap { it.allSuperTypes(declarations) }
    }

    private fun AbiClass.allDeclarationsIncludingInherited(
        oldLibraryDeclarations: Map<String, AbiDeclaration>
    ): List<AbiDeclaration> {
        // Collect all the declarations directly on the class (without functions) +
        // + all functions, (including inherited). The filterNot is to avoid listing
        // functions directly on the class twice.
        return declarations.filterNot { it is AbiFunction } +
            allMethodsIncludingInherited(oldLibraryDeclarations)
    }

    private fun AbiClass.allMethodsIncludingInherited(
        oldLibraryDeclarations: Map<String, AbiDeclaration>
    ): List<AbiFunction> {
        val functionMap =
            declarations
                .filterIsInstance<AbiFunction>()
                .associateBy { it.asUnqualifiedTypeString() }
                .toMutableMap()
        superTypes
            .map {
                // we should throw here if we can't find the class in the package/dependencies
                oldLibraryDeclarations[it.asString()]
            }
            .filterIsInstance<AbiClass>()
            .flatMap { it.allMethodsIncludingInherited(oldLibraryDeclarations) }
            .associateBy { it.asUnqualifiedTypeString() }
            .forEach { (key, func) -> functionMap.putIfAbsent(key, func) }
        return functionMap.values.toList()
    }

    private fun AbiFunction.isBinaryCompatibleWith(
        otherFunction: AbiFunction,
        errors: CompatibilityErrors
    ) {
        if (isConstructor != otherFunction.isConstructor) {
            errors.add(
                "isConstructor changed from ${otherFunction.isConstructor} to " +
                    "$isConstructor for $qualifiedName"
            )
        }
        if (modality != otherFunction.modality) {
            when {
                modality == AbiModality.OPEN && otherFunction.modality == AbiModality.ABSTRACT ->
                    Unit
                else ->
                    errors.add(
                        "modality changed from ${otherFunction.modality} to " +
                            "$modality for $qualifiedName"
                    )
            }
        }
        if (isInline != otherFunction.isInline) {
            when {
                isInline && !otherFunction.isInline -> Unit
                else ->
                    errors.add(
                        "isInline changed from ${otherFunction.isInline} to $isInline " +
                            "for $qualifiedName"
                    )
            }
        }
        if (isSuspend != otherFunction.isSuspend) {
            errors.add(
                "isSuspend changed from ${otherFunction.isSuspend} to $isSuspend for " +
                    "$qualifiedName"
            )
        }
        if (hasExtensionReceiverParameter != otherFunction.hasExtensionReceiverParameter) {
            errors.add(
                "hasExtensionReceiverParameter changed from " +
                    "${otherFunction.hasExtensionReceiverParameter} to " +
                    "$hasExtensionReceiverParameter for $qualifiedName"
            )
        }
        if (contextReceiverParametersCount != otherFunction.contextReceiverParametersCount) {
            errors.add(
                "contextReceiverParametersCount changed from " +
                    "${otherFunction.contextReceiverParametersCount} to " +
                    "$contextReceiverParametersCount for $qualifiedName"
            )
        }
        returnType.isBinaryCompatibleWith(
            otherFunction.returnType,
            qualifiedName.toString(),
            errors,
            "Return type"
        )

        // bake the index into the data type for clearer reporting in error messages
        val decoratedValueParameters: List<DecoratedAbiValueParameter> =
            valueParameters.mapIndexed { index, valueParameter ->
                DecoratedAbiValueParameter(index, valueParameter)
            }
        // by the time we get here, we already know that there are the same number of value
        // parameters and that they have the same type. If they didn't they would be considered
        // to be different functions. The following check is to give more detailed compatibility
        // details on things like whether a param has a default, is vararg, etc
        decoratedValueParameters.isBinaryCompatibleWith(
            otherFunction.valueParameters.mapIndexed { index, valueParameter ->
                DecoratedAbiValueParameter(index, valueParameter)
            },
            entityName = "valueParameter",
            isAllowedAddition = { false },
            uniqueId = DecoratedAbiValueParameter::asString,
            isBinaryCompatibleWith = DecoratedAbiValueParameter::isBinaryCompatibleWith,
            parentQualifiedName = qualifiedName.toString(),
            errors = errors,
        )
        typeParameters.isBinaryCompatibleWith(
            otherFunction.typeParameters,
            entityName = "typeParameter",
            isAllowedAddition = { false },
            uniqueId = AbiTypeParameter::tag,
            isBinaryCompatibleWith = AbiTypeParameter::isBinaryCompatibleWith,
            parentQualifiedName = qualifiedName.toString(),
            errors = errors
        )
    }

    private fun AbiProperty.isBinaryCompatibleWith(
        oldProperty: AbiProperty,
        errors: CompatibilityErrors
    ) {
        if (kind != oldProperty.kind) {
            when {
                kind == AbiPropertyKind.CONST_VAL && oldProperty.kind == AbiPropertyKind.VAL -> Unit
                modality == AbiModality.FINAL &&
                    kind == AbiPropertyKind.VAR &&
                    oldProperty.kind == AbiPropertyKind.VAL -> Unit
                // changing var to val is allowed as long as the setter was private / internal (null
                // in dump)
                oldProperty.kind == AbiPropertyKind.VAR &&
                    kind == AbiPropertyKind.VAL &&
                    oldProperty.setter == null -> Unit
                else ->
                    errors.add("kind changed from ${oldProperty.kind} to $kind for $qualifiedName")
            }
        }
        val newGetter = getter
        val oldGetter = oldProperty.getter
        if (oldGetter != null && newGetter == null) {
            errors.add("removed getter from $qualifiedName")
        } else if (oldGetter != null && newGetter != null) {
            newGetter.isBinaryCompatibleWith(oldGetter, errors)
        }

        val newSetter = setter
        val oldSetter = oldProperty.setter
        if (oldSetter != null && newSetter == null) {
            errors.add("removed setter from $qualifiedName")
        } else if (oldSetter != null && newSetter != null) {
            newSetter.isBinaryCompatibleWith(oldSetter, errors)
        }
    }

    companion object {
        fun checkAllBinariesAreCompatible(
            newLibraries: Map<String, LibraryAbi>,
            oldLibraries: Map<String, LibraryAbi>,
            baselines: Set<String> = emptySet(),
            validate: Boolean = true
        ): List<CompatibilityError> {
            val removedTargets = oldLibraries.keys - newLibraries.keys
            if (removedTargets.isNotEmpty()) {
                val errors =
                    removedTargets.flatMap {
                        CompatibilityErrors(baselines, it).apply { add("Target was removed") }
                    }
                if (validate) {
                    throw ValidationException(errors.toString())
                }
                return errors
            }
            return oldLibraries.keys.flatMap { target ->
                val newLib = newLibraries[target]!!
                val oldLib = oldLibraries[target]!!
                val errors = CompatibilityErrors(baselines, target)
                BinaryCompatibilityChecker(newLib, oldLib)
                    .checkBinariesAreCompatible(errors, validate)
            }
        }

        fun checkAllBinariesAreCompatible(
            newLibraries: Map<String, LibraryAbi>,
            oldLibraries: Map<String, LibraryAbi>,
            baselineFile: File?,
            validate: Boolean = true
        ) =
            checkAllBinariesAreCompatible(
                newLibraries,
                oldLibraries,
                baselineFile?.asBaselineErrors() ?: emptySet(),
                validate
            )
    }
}

internal fun AbiTypeParameter.isBinaryCompatibleWith(
    otherTypeParam: AbiTypeParameter,
    parentQualifiedName: String,
    errors: CompatibilityErrors
) {
    if (isReified != otherTypeParam.isReified) {
        when {
            !isReified && otherTypeParam.isReified -> Unit
            else ->
                errors.add(
                    "isReified changed from ${otherTypeParam.isReified} to $isReified " +
                        "for type param $tag on $parentQualifiedName"
                )
        }
    }
    if (variance != otherTypeParam.variance) {
        errors.add(
            "variance changed from ${otherTypeParam.variance} to $variance " +
                "for type param $tag on $parentQualifiedName"
        )
    }
    val upperBound = upperBounds.singleOrNull()
    val otherUpperBound = otherTypeParam.upperBounds.singleOrNull()
    if (upperBound == null && otherUpperBound == null) {
        return
    }
    if (upperBounds.isUnbounded() && otherTypeParam.upperBounds.isUnbounded()) {
        return
    }
    if (upperBound.valueAsString != otherUpperBound.valueAsString) {
        errors.add(
            "upper bounds changed from ${otherUpperBound.valueAsString} to " +
                "${upperBound.valueAsString} type param $tag on $parentQualifiedName"
        )
    }
}

private val AbiType?.valueAsString: String
    get() = this?.classNameOrTag ?: "Unit / null"

private fun List<AbiType>.isUnbounded(): Boolean =
    isEmpty() || single().className?.toString() == "kotlin/Any"

private fun DecoratedAbiValueParameter.isBinaryCompatibleWith(
    otherParam: DecoratedAbiValueParameter,
    parentQualifiedName: String,
    errors: CompatibilityErrors
) {
    type.isBinaryCompatibleWith(otherParam.type, parentQualifiedName, errors)
    if (isVararg != otherParam.isVararg) {
        errors.add(
            "isVararg changed from ${otherParam.isVararg} to $isVararg for parameter " +
                "${asString()} of $parentQualifiedName"
        )
    }
    if (hasDefaultArg != otherParam.hasDefaultArg) {
        when {
            hasDefaultArg && !otherParam.hasDefaultArg -> Unit
            else ->
                errors.add(
                    "hasDefaultArg changed from ${otherParam.hasDefaultArg} to $hasDefaultArg for " +
                        "parameter ${asString()} of $parentQualifiedName"
                )
        }
    }
    if (isNoinline != otherParam.isNoinline) {
        errors.add(
            "isNoinline changed from ${otherParam.isNoinline} to $isNoinline for " +
                "parameter ${asString()} of $parentQualifiedName"
        )
    }
    if (isCrossinline != otherParam.isCrossinline) {
        errors.add(
            "isCrossinline changed from ${otherParam.isCrossinline} to $isCrossinline for " +
                "parameter ${asString()} of $parentQualifiedName"
        )
    }
}

private fun AbiType?.isBinaryCompatibleWith(
    otherType: AbiType?,
    parentQualifiedName: String,
    errors: CompatibilityErrors,
) = isBinaryCompatibleWith(otherType, parentQualifiedName, errors, "Type")

private fun AbiType?.isBinaryCompatibleWith(
    otherType: AbiType?,
    parentQualifiedName: String,
    errors: CompatibilityErrors,
    kind: String = "type"
) {
    if (valueAsString != otherType.valueAsString) {
        errors.add(
            "$kind changed from ${otherType.valueAsString} to " +
                "$valueAsString for $parentQualifiedName"
        )
        return
    }
    if ((this == null) && otherType == null) {
        return
    }
    when {
        this is AbiType.Simple ->
            isBinaryCompatible(otherType as AbiType.Simple, parentQualifiedName, errors, kind)
    }
}

private fun AbiType.Simple.isBinaryCompatible(
    otherType: AbiType.Simple,
    parentQualifiedName: String,
    errors: CompatibilityErrors,
    kind: String
) {
    val classifierRef = classifierReference
    val otherClassifierRef = otherType.classifierReference
    val typeMatches =
        when (classifierReference) {
            is ClassReference -> {
                classifierRef.className == otherClassifierRef.className
            }
            is TypeParameterReference -> {
                classifierRef.tag == otherClassifierRef.tag
            }
        }
    if (!typeMatches) {
        errors.add("$kind did not match for $parentQualifiedName")
        return
    }
    if (nullability != otherType.nullability) {
        errors.add("$kind nullability did not match for $parentQualifiedName")
        return
    }
    arguments.isBinaryCompatibleWith(
        otherType.arguments,
        entityName = "typeArgument",
        isAllowedAddition = { false },
        uniqueId = AbiTypeArgument::asString,
        isBinaryCompatibleWith = AbiTypeArgument::isBinaryCompatibleWith,
        parentQualifiedName = parentQualifiedName,
        errors = errors
    )
}

private fun AbiTypeArgument.isBinaryCompatibleWith(
    otherTypeArgument: AbiTypeArgument,
    parentQualifiedName: String,
    errors: CompatibilityErrors
) {
    if (this is StarProjection && otherTypeArgument is StarProjection) {
        return
    }
    if (this !is TypeProjection || otherTypeArgument !is TypeProjection) {
        errors.add("Star projection and type projection don't match")
        return
    }
    if (variance != otherTypeArgument.variance) {
        errors.add("variance changed for type arg ${type.asString()}")
    }
    type.isBinaryCompatibleWith(otherTypeArgument.type, parentQualifiedName, errors)
}

private fun AbiDeclaration.asTypeString() =
    when (this) {
        is AbiFunction -> qualifiedName.toString() + valueParameterString()
        else -> qualifiedName.toString()
    }

private fun AbiDeclaration.asUnqualifiedTypeString(): String {
    val name = qualifiedName.relativeName.nameSegments.last().value
    return when (this) {
        is AbiFunction -> name + valueParameterString()
        else -> name
    }
}

private fun AbiFunction.valueParameterString() =
    "(${valueParameters.joinToString(", ") { it.type.asString() }})"

private fun AbiType.asString() =
    when (this) {
        is AbiType.Dynamic -> "dynamic"
        is AbiType.Error -> "error"
        is AbiType.Simple ->
            when (classifierReference) {
                is ClassReference -> (classifierReference as ClassReference).className.toString()
                is TypeParameterReference -> (classifierReference as TypeParameterReference).tag
            }
    }

private fun DecoratedAbiValueParameter.asString() = "$index: ${type.asString()}"

private fun AbiTypeArgument.asString() =
    when (this) {
        is StarProjection -> "*"
        is TypeProjection -> type.asString()
    }

class ValidationException(errorMessage: String) : RuntimeException(errorMessage)

private fun LibraryAbi.allDeclarations(): List<AbiDeclaration> {
    val allDeclarations = mutableListOf<AbiDeclaration>()
    topLevelDeclarations.declarations.forEach { allDeclarations.addAll(it.allDeclarations()) }
    return allDeclarations
}

private fun AbiDeclaration.allDeclarations(): List<AbiDeclaration> {
    return listOf(this) +
        when (this) {
            is AbiEnumEntry -> emptyList()
            is AbiFunction -> emptyList()
            is AbiProperty -> listOfNotNull(getter, setter)
            is AbiClass -> declarations.flatMap { it.allDeclarations() }
        }
}

/**
 * Checks if any list of items which may be binary compatible with their previous version is
 * compatible. We have to do this a lot (methods, value parameters, super types, type args) and
 * although they have slightly different rules about when things can be added / removed they follow
 * the same basic pattern.
 */
private fun <T> List<T>.isBinaryCompatibleWith(
    oldEntitiesList: List<T>,
    entityName: String,
    uniqueId: T.() -> String,
    isBinaryCompatibleWith: T.(T, String, CompatibilityErrors) -> Unit,
    isAllowedAddition: T.() -> Boolean = { true },
    parentQualifiedName: String,
    errors: CompatibilityErrors
) {
    val oldEntities = oldEntitiesList.associateBy { it.uniqueId() }
    val newEntities = associateBy { it.uniqueId() }
    val removedEntities = oldEntities.keys - newEntities.keys
    removedEntities.forEach { errors.add("Removed $entityName $it from $parentQualifiedName") }
    val addedEntities = newEntities.keys - oldEntities.keys
    val disallowedAdditions = addedEntities.filterNot { newEntities[it]!!.isAllowedAddition() }
    disallowedAdditions.forEach { errors.add("Added $entityName $it to $parentQualifiedName") }
    for ((id, oldEntity) in oldEntities) {
        // if the entity is missing we'll add an error for that above, but we continue to compare
        // entities to show as many violations at once as possible
        val newEntity: T = newEntities[id] ?: continue
        newEntity.isBinaryCompatibleWith(oldEntity, parentQualifiedName, errors)
    }
}

class CompatibilityErrors(private val baselines: Set<String>, val target: String) :
    MutableList<CompatibilityError> by mutableListOf() {
    fun add(
        message: String,
        severity: CompatibilityErrorSeverity = CompatibilityErrorSeverity.ERROR
    ) {
        val error = CompatibilityError(message, target, severity)
        if (baselines.contains(error.toString())) {
            return
        }
        add(error)
    }

    override fun toString(): String = joinToString("\n")
}

data class CompatibilityError(
    private val message: String,
    val target: String,
    val severity: CompatibilityErrorSeverity,
) {
    override fun toString(): String {
        return "[$target]: $message"
    }
}

enum class CompatibilityErrorSeverity() {
    ERROR
}

private fun File.asBaselineErrors(): Set<String> =
    readLines().toMutableList().let {
        val formatVersion =
            try {
                it.removeFirst().split(":").last().trim()
            } catch (e: Exception) {
                throw RuntimeException("Failed to parse baseline version from '${this.path}'")
            }
        return when (formatVersion) {
            "1.0" -> it.toSet()
            else -> throw RuntimeException("Unrecognized baseline format: '$formatVersion'")
        }
    }

private class DecoratedAbiValueParameter(val index: Int, param: AbiValueParameter) :
    AbiValueParameter by param
