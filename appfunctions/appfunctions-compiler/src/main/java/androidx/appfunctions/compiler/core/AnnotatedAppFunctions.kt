/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.appfunctions.compiler.core

import androidx.appfunctions.compiler.core.AnnotatedAppFunctionSerializableProxy.ResolvedAnnotatedSerializableProxies
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.PRIMITIVE_ARRAY
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.PRIMITIVE_LIST
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.PRIMITIVE_SINGULAR
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.SERIALIZABLE_LIST
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.SERIALIZABLE_PROXY_LIST
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.SERIALIZABLE_PROXY_SINGULAR
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.AppFunctionSupportedTypeCategory.SERIALIZABLE_SINGULAR
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.Companion.SUPPORTED_TYPES_STRING
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.Companion.isSupportedType
import androidx.appfunctions.compiler.core.AppFunctionTypeReference.Companion.toAppFunctionDatatype
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionAnnotation
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionContextClass
import androidx.appfunctions.compiler.core.IntrospectionHelper.AppFunctionSchemaDefinitionAnnotation
import androidx.appfunctions.metadata.AppFunctionAllOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionPrimitiveTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionSchemaMetadata
import androidx.appfunctions.metadata.CompileTimeAppFunctionMetadata
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName

/**
 * Represents a collection of functions within a specific class that are annotated as app functions.
 */
data class AnnotatedAppFunctions(
    /** The [KSClassDeclaration] of the class that contains the annotated app functions. */
    val classDeclaration: KSClassDeclaration,
    /** The list of [KSFunctionDeclaration] that are annotated as app function. */
    val appFunctionDeclarations: List<KSFunctionDeclaration>,
) {
    /** Gets all annotated nodes. */
    fun getAllAnnotated(): List<KSAnnotated> {
        return buildList {
            // Only functions are annotated.
            for (appFunctionDeclaration in appFunctionDeclarations) {
                add(appFunctionDeclaration)
            }
        }
    }

    /**
     * Validates if the AppFunction implementation is valid.
     *
     * @throws SymbolNotReadyException if any related nodes are not ready for processing yet.
     */
    fun validate(): AnnotatedAppFunctions {
        if (!classDeclaration.validate()) {
            throw SymbolNotReadyException(
                "AppFunction enclosing class not ready for processing yet",
                classDeclaration,
            )
        }
        for (appFunction in appFunctionDeclarations) {
            if (!appFunction.validate()) {
                throw SymbolNotReadyException(
                    "AppFunction method not ready for processing yet",
                    appFunction,
                )
            }
        }
        validateFirstParameter()
        validateParameterTypes()
        return this
    }

    private fun validateFirstParameter() {
        for (appFunctionDeclaration in appFunctionDeclarations) {
            val firstParam = appFunctionDeclaration.parameters.firstOrNull()
            if (firstParam == null) {
                throw ProcessingException(
                    "The first parameter of an app function must be " +
                        "${AppFunctionContextClass.CLASS_NAME}",
                    appFunctionDeclaration,
                )
            }
            if (!firstParam.type.isOfType(AppFunctionContextClass.CLASS_NAME)) {
                throw ProcessingException(
                    "The first parameter of an app function must be " +
                        "${AppFunctionContextClass.CLASS_NAME}",
                    firstParam,
                )
            }
        }
    }

    private fun validateParameterTypes() {
        for (appFunctionDeclaration in appFunctionDeclarations) {
            for ((paramIndex, ksValueParameter) in appFunctionDeclaration.parameters.withIndex()) {
                if (paramIndex == 0) {
                    // Skip the first parameter which is always the `AppFunctionContext`.
                    continue
                }

                if (!isSupportedType(ksValueParameter.type)) {
                    throw ProcessingException(
                        "App function parameters must be a supported type, or a type " +
                            "annotated as @AppFunctionSerializable. See list of supported types:\n" +
                            "${
                                SUPPORTED_TYPES_STRING
                            }\n" +
                            "but found ${
                                AppFunctionTypeReference(ksValueParameter.type)
                                    .selfOrItemTypeReference.ensureQualifiedTypeName()
                                    .asString()
                            }",
                        ksValueParameter,
                    )
                }
            }
        }
    }

    /**
     * Gets the identifier of an app functions.
     *
     * The format of the identifier is `packageName.className#methodName`.
     */
    fun getAppFunctionIdentifier(functionDeclaration: KSFunctionDeclaration): String {
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val methodName = functionDeclaration.simpleName.asString()
        return "${packageName}.${className}#${methodName}"
    }

    /**
     * Returns the set of files that need to be processed to obtain the complete information about
     * the app functions defined in this class.
     *
     * This includes the class file containing the function declarations, the class file containing
     * the schema definitions, and the class files containing the AppFunctionSerializable classes
     * used in the function parameters.
     */
    fun getSourceFiles(): Set<KSFile> {
        val sourceFileSet: MutableSet<KSFile> = mutableSetOf()

        // Add the class file containing the function declarations
        classDeclaration.containingFile?.let { sourceFileSet.add(it) }

        for (functionDeclaration in appFunctionDeclarations) {
            // Add the class file containing the schema definitions
            val rootAppFunctionSchemaInterface =
                findRootAppFunctionSchemaInterface(functionDeclaration)
            rootAppFunctionSchemaInterface?.containingFile?.let { sourceFileSet.add(it) }

            // Traverse each functions parameter to obtain the relevant AppFunctionSerializable
            // class files
            for ((paramIndex, ksValueParameter) in functionDeclaration.parameters.withIndex()) {
                if (paramIndex == 0) {
                    // Skip the first parameter which is always the `AppFunctionContext`.
                    continue
                }
                val parameterTypeReference = AppFunctionTypeReference(ksValueParameter.type)
                if (parameterTypeReference.typeOrItemTypeIsAppFunctionSerializable()) {
                    sourceFileSet.addAll(
                        getAnnotatedAppFunctionSerializable(parameterTypeReference)
                            .getTransitiveSerializableSourceFiles()
                    )
                }
            }

            val returnTypeReference =
                AppFunctionTypeReference(checkNotNull(functionDeclaration.returnType))
            if (returnTypeReference.typeOrItemTypeIsAppFunctionSerializable()) {
                sourceFileSet.addAll(
                    getAnnotatedAppFunctionSerializable(returnTypeReference)
                        .getTransitiveSerializableSourceFiles()
                )
            }
        }
        return sourceFileSet
    }

    /** Gets the [classDeclaration]'s [ClassName]. */
    fun getEnclosingClassName(): ClassName {
        return ClassName(
            classDeclaration.packageName.asString(),
            classDeclaration.simpleName.asString(),
        )
    }

    /**
     * Creates a list of [CompileTimeAppFunctionMetadata]] instances for each of the app functions
     * defined in this class.
     */
    fun createAppFunctionMetadataList(
        resolvedAnnotatedSerializableProxies: ResolvedAnnotatedSerializableProxies
    ): List<CompileTimeAppFunctionMetadata> {
        return appFunctionDeclarations.map { functionDeclaration ->
            // Defining the shared types locally for this iteration is to isolate the components
            // used per function. This is done with the expectation that they can be globally
            // merged without encountering mismatching datatype metadata for the same object key.
            val sharedDataTypeMap: MutableMap<String, AppFunctionDataTypeMetadata> = mutableMapOf()
            val seenDataTypeQualifiers: MutableSet<String> = mutableSetOf()

            val appFunctionAnnotationProperties =
                computeAppFunctionAnnotationProperties(functionDeclaration)
            val parameterTypeMetadataList =
                functionDeclaration.buildParameterTypeMetadataList(
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )
            val responseTypeMetadata =
                checkNotNull(functionDeclaration.returnType)
                    .toAppFunctionDataTypeMetadata(
                        sharedDataTypeMap,
                        seenDataTypeQualifiers,
                        resolvedAnnotatedSerializableProxies
                    )

            CompileTimeAppFunctionMetadata(
                id = getAppFunctionIdentifier(functionDeclaration),
                isEnabledByDefault = appFunctionAnnotationProperties.isEnabledByDefault,
                schema = appFunctionAnnotationProperties.toAppFunctionSchemaMetadata(),
                parameters = parameterTypeMetadataList,
                response = AppFunctionResponseMetadata(valueType = responseTypeMetadata),
                components = AppFunctionComponentsMetadata(dataTypes = sharedDataTypeMap),
            )
        }
    }

    /** Builds a list of [AppFunctionParameterMetadata] for the parameters of an app function. */
    private fun KSFunctionDeclaration.buildParameterTypeMetadataList(
        sharedDataTypeMap: MutableMap<String, AppFunctionDataTypeMetadata>,
        seenDataTypeQualifiers: MutableSet<String>,
        resolvedAnnotatedSerializableProxies: ResolvedAnnotatedSerializableProxies
    ): List<AppFunctionParameterMetadata> = buildList {
        for (ksValueParameter in parameters) {
            if (ksValueParameter.type.isOfType(AppFunctionContextClass.CLASS_NAME)) {
                // Skip the first parameter which is always the `AppFunctionContext`.
                continue
            }

            val parameterName = checkNotNull(ksValueParameter.name).asString()
            val dataTypeMetadata =
                ksValueParameter.type.toAppFunctionDataTypeMetadata(
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )

            add(
                AppFunctionParameterMetadata(
                    name = parameterName,
                    // TODO(b/394553462): Parse required state from annotation.
                    isRequired = true,
                    dataType = dataTypeMetadata,
                )
            )
        }
    }

    private fun KSTypeReference.toAppFunctionDataTypeMetadata(
        sharedDataTypeMap: MutableMap<String, AppFunctionDataTypeMetadata>,
        seenDataTypeQualifiers: MutableSet<String>,
        resolvedAnnotatedSerializableProxies: ResolvedAnnotatedSerializableProxies
    ): AppFunctionDataTypeMetadata {
        val appFunctionTypeReference = AppFunctionTypeReference(this)
        return when (appFunctionTypeReference.typeCategory) {
            PRIMITIVE_SINGULAR ->
                AppFunctionPrimitiveTypeMetadata(
                    type = appFunctionTypeReference.toAppFunctionDataType(),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            PRIMITIVE_ARRAY ->
                AppFunctionArrayTypeMetadata(
                    itemType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = appFunctionTypeReference.determineArrayItemType(),
                            isNullable = false,
                        ),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            PRIMITIVE_LIST ->
                AppFunctionArrayTypeMetadata(
                    itemType =
                        AppFunctionPrimitiveTypeMetadata(
                            type = appFunctionTypeReference.determineArrayItemType(),
                            isNullable =
                                AppFunctionTypeReference(appFunctionTypeReference.itemTypeReference)
                                    .isNullable,
                        ),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            SERIALIZABLE_SINGULAR -> {
                val annotatedAppFunctionSerializable =
                    getAnnotatedAppFunctionSerializable(appFunctionTypeReference)
                addSerializableTypeMetadataToSharedDataTypeMap(
                    annotatedAppFunctionSerializable,
                    annotatedAppFunctionSerializable
                        .getProperties()
                        .associateBy { checkNotNull(it.name).toString() }
                        .toMutableMap(),
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )
                AppFunctionReferenceTypeMetadata(
                    referenceDataType =
                        appFunctionTypeReference.selfTypeReference
                            .toTypeName()
                            .ignoreNullable()
                            .toString(),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            }
            SERIALIZABLE_LIST -> {
                val annotatedAppFunctionSerializable =
                    getAnnotatedAppFunctionSerializable(appFunctionTypeReference)
                addSerializableTypeMetadataToSharedDataTypeMap(
                    annotatedAppFunctionSerializable,
                    annotatedAppFunctionSerializable
                        .getProperties()
                        .associateBy { checkNotNull(it.name).toString() }
                        .toMutableMap(),
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )
                AppFunctionArrayTypeMetadata(
                    itemType =
                        AppFunctionReferenceTypeMetadata(
                            referenceDataType =
                                appFunctionTypeReference.itemTypeReference
                                    .toTypeName()
                                    .ignoreNullable()
                                    .toString(),
                            isNullable =
                                AppFunctionTypeReference(appFunctionTypeReference.itemTypeReference)
                                    .isNullable,
                        ),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            }
            SERIALIZABLE_PROXY_SINGULAR -> {
                val targetSerializableProxy =
                    resolvedAnnotatedSerializableProxies.getSerializableProxyForTypeReference(
                        appFunctionTypeReference
                    )
                addSerializableTypeMetadataToSharedDataTypeMap(
                    targetSerializableProxy,
                    targetSerializableProxy
                        .getProperties()
                        .associateBy { checkNotNull(it.name).toString() }
                        .toMutableMap(),
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )
                AppFunctionReferenceTypeMetadata(
                    referenceDataType =
                        appFunctionTypeReference.selfTypeReference
                            .toTypeName()
                            .ignoreNullable()
                            .toString(),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            }
            SERIALIZABLE_PROXY_LIST -> {
                val targetSerializableProxy =
                    resolvedAnnotatedSerializableProxies.getSerializableProxyForTypeReference(
                        appFunctionTypeReference
                    )
                addSerializableTypeMetadataToSharedDataTypeMap(
                    targetSerializableProxy,
                    targetSerializableProxy
                        .getProperties()
                        .associateBy { checkNotNull(it.name).toString() }
                        .toMutableMap(),
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )
                AppFunctionArrayTypeMetadata(
                    itemType =
                        AppFunctionReferenceTypeMetadata(
                            referenceDataType =
                                appFunctionTypeReference.itemTypeReference
                                    .toTypeName()
                                    .ignoreNullable()
                                    .toString(),
                            isNullable =
                                AppFunctionTypeReference(appFunctionTypeReference.itemTypeReference)
                                    .isNullable,
                        ),
                    isNullable = appFunctionTypeReference.isNullable,
                )
            }
        }
    }

    /**
     * Adds the [AppFunctionDataTypeMetadata] for a serializable/capability type to the shared data
     * type map.
     *
     * @param appFunctionSerializableType the [AnnotatedAppFunctionSerializable] for the
     *   serializable or capability type being processed.
     * @param unvisitedSerializableProperties a map of unvisited serializable properties. This map
     *   is used to track the properties that have not yet been visited. The map is updated as the
     *   properties are visited.
     * @param sharedDataTypeMap a map of shared data types. This map is used to store the
     *   [AppFunctionDataTypeMetadata] for all serializable/capability types that are used in an app
     *   function. This map is used to avoid duplicating the metadata for the same serializable
     *   type.
     * @param seenDataTypeQualifiers a set of seen data type qualifiers. This set is used to avoid
     *   processing the same serializable type multiple times.
     * @param resolvedAnnotatedSerializableProxies The resolved annotated serializable proxies.
     */
    // TODO: Document traversal rules.
    private fun addSerializableTypeMetadataToSharedDataTypeMap(
        appFunctionSerializableType: AnnotatedAppFunctionSerializable,
        unvisitedSerializableProperties: MutableMap<String, AppFunctionPropertyDeclaration>,
        sharedDataTypeMap: MutableMap<String, AppFunctionDataTypeMetadata>,
        seenDataTypeQualifiers: MutableSet<String>,
        resolvedAnnotatedSerializableProxies: ResolvedAnnotatedSerializableProxies
    ) {
        val serializableTypeQualifiedName =
            if (appFunctionSerializableType is AnnotatedAppFunctionSerializableProxy) {
                checkNotNull(appFunctionSerializableType.targetClassDeclaration.qualifiedName)
                    .asString()
            } else {
                appFunctionSerializableType.qualifiedName
            }
        // This type has already been added to the sharedDataMap.
        if (seenDataTypeQualifiers.contains(serializableTypeQualifiedName)) {
            return
        }
        seenDataTypeQualifiers.add(serializableTypeQualifiedName)

        val superTypesWithSerializableAnnotation =
            appFunctionSerializableType.findSuperTypesWithSerializableAnnotation()
        val superTypesWithCapabilityAnnotation =
            appFunctionSerializableType.findSuperTypesWithCapabilityAnnotation()
        if (
            superTypesWithSerializableAnnotation.isEmpty() &&
                superTypesWithCapabilityAnnotation.isEmpty()
        ) {
            // If there is no super type, then this is a base serializable object.
            sharedDataTypeMap.put(
                serializableTypeQualifiedName,
                buildObjectTypeMetadataForObjectParameters(
                    serializableTypeQualifiedName,
                    appFunctionSerializableType.getProperties(),
                    unvisitedSerializableProperties,
                    sharedDataTypeMap,
                    seenDataTypeQualifiers,
                    resolvedAnnotatedSerializableProxies
                )
            )
        } else {
            // If there are superTypes, we first need to build the list of superTypes for this
            // serializable to match.
            val matchAllSuperTypesList: List<AppFunctionDataTypeMetadata> = buildList {
                for (serializableSuperType in superTypesWithSerializableAnnotation) {
                    addSerializableTypeMetadataToSharedDataTypeMap(
                        AnnotatedAppFunctionSerializable(serializableSuperType),
                        unvisitedSerializableProperties,
                        sharedDataTypeMap,
                        seenDataTypeQualifiers,
                        resolvedAnnotatedSerializableProxies
                    )
                    add(
                        AppFunctionReferenceTypeMetadata(
                            referenceDataType =
                                checkNotNull(serializableSuperType.toClassName().canonicalName),
                            // Shared type should be the most permissive version (i.e. nullable) by
                            // default. This is because the outer AllOfType to this shared type
                            // can add further constraint (i.e. non-null) if required.
                            isNullable = true
                        )
                    )
                }

                for (capabilitySuperType in superTypesWithCapabilityAnnotation) {
                    add(
                        buildObjectTypeMetadataForObjectParameters(
                            checkNotNull(capabilitySuperType.toClassName().canonicalName),
                            capabilitySuperType
                                .getDeclaredProperties()
                                .map { AppFunctionPropertyDeclaration(it) }
                                .toList(),
                            unvisitedSerializableProperties,
                            sharedDataTypeMap,
                            seenDataTypeQualifiers,
                            resolvedAnnotatedSerializableProxies
                        )
                    )
                }

                if (unvisitedSerializableProperties.isNotEmpty()) {
                    // Since all superTypes have been visited, then the remaining parameters in the
                    // unvisitedSerializableParameters map belong to the subclass directly.
                    add(
                        buildObjectTypeMetadataForObjectParameters(
                            serializableTypeQualifiedName,
                            unvisitedSerializableProperties.values.toList(),
                            unvisitedSerializableProperties,
                            sharedDataTypeMap,
                            seenDataTypeQualifiers,
                            resolvedAnnotatedSerializableProxies
                        )
                    )
                }
            }

            // Finally add allOf the datatypes required to build this composed objects to the
            // components map
            sharedDataTypeMap.put(
                serializableTypeQualifiedName,
                AppFunctionAllOfTypeMetadata(
                    qualifiedName = serializableTypeQualifiedName,
                    matchAll = matchAllSuperTypesList,
                    // Shared type should be the most permissive version (i.e. nullable) by
                    // default. This is because the outer ReferenceType to this shared type
                    // can add further constraint (i.e. non-null) if required.
                    isNullable = true
                )
            )
        }
    }

    /**
     * Builds an [AppFunctionObjectTypeMetadata] for a serializable/capability type.
     *
     * @param typeQualifiedName the qualified name of the serializable/capability type being
     *   processed. This is the qualified name of the class that is annotated with
     *   [androidx.appfunctions.AppFunctionSerializable] or
     *   [androidx.appfunctions.AppFunctionSchemaCapability].
     * @param currentPropertiesList the list of properties from the serializable/capability class
     *   that is being processed.
     * @param unvisitedSerializableProperties a map of unvisited serializable properties. This map
     *   is used to track the properties that have not yet been visited. The map is updated as the
     *   properties are visited. The map should be a superset of the [currentPropertiesList] as it
     *   can contain other properties belonging to a subclass of the current [typeQualifiedName]
     *   class being processed.
     * @param sharedDataTypeMap a map of shared data types. This map is used to store the
     *   [AppFunctionDataTypeMetadata] for all serializable types that are used in an app function.
     *   This map is used to avoid duplicating the metadata for the same serializable type.
     * @param seenDataTypeQualifiers a set of seen data type qualifiers. This set is used to avoid
     *   processing the same serializable type multiple times.
     * @param resolvedAnnotatedSerializableProxies The resolved annotated serializable proxies.
     * @return an [AppFunctionObjectTypeMetadata] for the serializable type.
     */
    private fun buildObjectTypeMetadataForObjectParameters(
        typeQualifiedName: String,
        currentPropertiesList: List<AppFunctionPropertyDeclaration>,
        unvisitedSerializableProperties: MutableMap<String, AppFunctionPropertyDeclaration>,
        sharedDataTypeMap: MutableMap<String, AppFunctionDataTypeMetadata>,
        seenDataTypeQualifiers: MutableSet<String>,
        resolvedAnnotatedSerializableProxies: ResolvedAnnotatedSerializableProxies
    ): AppFunctionObjectTypeMetadata {
        val currentSerializableProperties: List<AppFunctionPropertyDeclaration> = buildList {
            for (property in currentPropertiesList) {
                // This property has now been visited. Remove it from the
                // unvisitedSerializableProperties map so that we don't visit it again when
                // processing the rest of a sub-class that implements this superclass.
                // This is because before processing a subclass we process its superclass first
                // so the unvisitedSerializableProperties could still contain properties not
                // directly included in the current class being processed.
                add(checkNotNull(unvisitedSerializableProperties.remove(property.name)))
            }
        }
        return buildObjectTypeMetadataForObjectProperty(
            typeQualifiedName,
            currentSerializableProperties,
            sharedDataTypeMap,
            seenDataTypeQualifiers,
            resolvedAnnotatedSerializableProxies
        )
    }

    private fun buildObjectTypeMetadataForObjectProperty(
        serializableTypeQualifiedName: String,
        currentSerializableProperties: List<AppFunctionPropertyDeclaration>,
        sharedDataTypeMap: MutableMap<String, AppFunctionDataTypeMetadata>,
        seenDataTypeQualifiers: MutableSet<String>,
        resolvedAnnotatedSerializableProxies: ResolvedAnnotatedSerializableProxies
    ): AppFunctionObjectTypeMetadata {
        val requiredPropertiesList: MutableList<String> = mutableListOf()
        val appFunctionSerializablePropertiesMap: Map<String, AppFunctionDataTypeMetadata> =
            buildMap {
                for (property in currentSerializableProperties) {
                    val innerAppFunctionDataTypeMetadata =
                        property.type.toAppFunctionDataTypeMetadata(
                            sharedDataTypeMap,
                            seenDataTypeQualifiers,
                            resolvedAnnotatedSerializableProxies
                        )
                    put(property.name, innerAppFunctionDataTypeMetadata)
                    // TODO(b/394553462): Parse required state from annotation.
                    requiredPropertiesList.add(property.name)
                }
            }
        return AppFunctionObjectTypeMetadata(
            properties = appFunctionSerializablePropertiesMap,
            required = requiredPropertiesList,
            qualifiedName = serializableTypeQualifiedName,
            // Shared type should be the most permissive version (i.e. nullable) by default.
            // This is because the outer ReferenceType to this shared type can add further
            // constraint (i.e. non-null) if required.
            isNullable = true,
        )
    }

    private fun AppFunctionTypeReference.toAppFunctionDataType(): Int {
        return when (this.typeCategory) {
            PRIMITIVE_SINGULAR -> selfTypeReference.toAppFunctionDatatype()
            SERIALIZABLE_PROXY_SINGULAR,
            SERIALIZABLE_SINGULAR -> AppFunctionObjectTypeMetadata.TYPE
            PRIMITIVE_ARRAY,
            PRIMITIVE_LIST,
            SERIALIZABLE_PROXY_LIST,
            SERIALIZABLE_LIST -> AppFunctionArrayTypeMetadata.TYPE
        }
    }

    private fun AppFunctionTypeReference.determineArrayItemType(): Int {
        return when (this.typeCategory) {
            SERIALIZABLE_LIST -> AppFunctionObjectTypeMetadata.TYPE
            PRIMITIVE_ARRAY -> selfTypeReference.toAppFunctionDatatype()
            PRIMITIVE_LIST -> itemTypeReference.toAppFunctionDatatype()
            SERIALIZABLE_PROXY_LIST -> itemTypeReference.toAppFunctionDatatype()
            PRIMITIVE_SINGULAR,
            SERIALIZABLE_PROXY_SINGULAR,
            SERIALIZABLE_SINGULAR ->
                throw ProcessingException(
                    "Not a supported array type " +
                        selfTypeReference.ensureQualifiedTypeName().asString(),
                    selfTypeReference,
                )
        }
    }

    private fun computeAppFunctionAnnotationProperties(
        functionDeclaration: KSFunctionDeclaration
    ): AppFunctionAnnotationProperties {
        val appFunctionAnnotation =
            functionDeclaration.annotations.findAnnotation(AppFunctionAnnotation.CLASS_NAME)
                ?: throw ProcessingException(
                    "Function not annotated with @AppFunction.",
                    functionDeclaration,
                )
        val enabled =
            appFunctionAnnotation.requirePropertyValueOfType(
                AppFunctionAnnotation.PROPERTY_IS_ENABLED,
                Boolean::class,
            )

        val rootInterfaceWithAppFunctionSchemaDefinition =
            findRootAppFunctionSchemaInterface(functionDeclaration)

        val schemaFunctionAnnotation =
            rootInterfaceWithAppFunctionSchemaDefinition
                ?.annotations
                ?.findAnnotation(AppFunctionSchemaDefinitionAnnotation.CLASS_NAME)
        val schemaCategory =
            schemaFunctionAnnotation?.requirePropertyValueOfType(
                AppFunctionSchemaDefinitionAnnotation.PROPERTY_CATEGORY,
                String::class,
            )
        val schemaName =
            schemaFunctionAnnotation?.requirePropertyValueOfType(
                AppFunctionSchemaDefinitionAnnotation.PROPERTY_NAME,
                String::class,
            )
        val schemaVersion =
            schemaFunctionAnnotation
                ?.requirePropertyValueOfType(
                    AppFunctionSchemaDefinitionAnnotation.PROPERTY_VERSION,
                    Int::class,
                )
                ?.toLong()

        return AppFunctionAnnotationProperties(enabled, schemaName, schemaVersion, schemaCategory)
    }

    private fun findRootAppFunctionSchemaInterface(
        function: KSFunctionDeclaration
    ): KSClassDeclaration? {
        val parentDeclaration = function.parentDeclaration as? KSClassDeclaration ?: return null

        // Check if the enclosing class has the @AppFunctionSchemaDefinition
        val annotation =
            parentDeclaration.annotations.findAnnotation(
                AppFunctionSchemaDefinitionAnnotation.CLASS_NAME
            )
        if (annotation != null) {
            return parentDeclaration
        }

        val superClassFunction = (function.findOverridee() as? KSFunctionDeclaration) ?: return null
        return findRootAppFunctionSchemaInterface(superClassFunction)
    }

    private fun getAnnotatedAppFunctionSerializable(
        appFunctionTypeReference: AppFunctionTypeReference
    ): AnnotatedAppFunctionSerializable {
        val appFunctionSerializableClassDeclaration =
            appFunctionTypeReference.selfOrItemTypeReference.resolve().declaration
                as KSClassDeclaration
        return AnnotatedAppFunctionSerializable(
                appFunctionSerializableClassDeclaration,
            )
            .parameterizedBy(appFunctionTypeReference.selfOrItemTypeReference.resolve().arguments)
            .validate()
    }

    private fun AppFunctionAnnotationProperties.toAppFunctionSchemaMetadata():
        AppFunctionSchemaMetadata? {
        return if (this.schemaName != null) {
            AppFunctionSchemaMetadata(
                category = checkNotNull(this.schemaCategory),
                name = this.schemaName,
                version = checkNotNull(this.schemaVersion),
            )
        } else {
            null
        }
    }

    private fun AppFunctionTypeReference.typeOrItemTypeIsAppFunctionSerializable(): Boolean {
        return this.isOfTypeCategory(SERIALIZABLE_SINGULAR) ||
            this.isOfTypeCategory(SERIALIZABLE_LIST)
    }

    private data class AppFunctionAnnotationProperties(
        val isEnabledByDefault: Boolean,
        val schemaName: String?,
        val schemaVersion: Long?,
        val schemaCategory: String?,
    )
}
