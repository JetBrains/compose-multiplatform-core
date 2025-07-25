package appfunctions_aggregated_deps

import androidx.appfunctions.`internal`.SchemaAppFunctionInventory
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionSchemaMetadata
import androidx.appfunctions.metadata.CompileTimeAppFunctionMetadata
import javax.`annotation`.processing.Generated
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

@Generated("androidx.appfunctions.compiler.AppFunctionCompiler")
public class `$SchemaAppFunctionInventory_Impl` : SchemaAppFunctionInventory() {
  override val functionIdToMetadataMap: Map<String, CompileTimeAppFunctionMetadata> = mapOf(
    "note/updateNote/2" to NoteUpdateNote2MetadataObject.APP_FUNCTION_METADATA,
  )

  private object NoteUpdateNote2MetadataObject {
    private val SCHEMA_METADATA: AppFunctionSchemaMetadata? =
        AppFunctionSchemaMetadata(category= "note", name="updateNote", version=2)

    private val PARAMETER_METADATA_LIST: List<AppFunctionParameterMetadata> = listOf(
    )

    private val REFERENCE_RESPONSE_VALUE_TYPE: AppFunctionReferenceTypeMetadata =
        AppFunctionReferenceTypeMetadata(
            referenceDataType = "placeholder",
            isNullable = false
        )

    private val RESPONSE_METADATA: AppFunctionResponseMetadata = AppFunctionResponseMetadata(
            valueType = REFERENCE_RESPONSE_VALUE_TYPE
        )

    private val COMPONENTS_METADATA_DATA_TYPES_MAP: Map<String, AppFunctionDataTypeMetadata> =
        mapOf(
      )

    private val COMPONENTS_METADATA: AppFunctionComponentsMetadata = AppFunctionComponentsMetadata(
            dataTypes = COMPONENTS_METADATA_DATA_TYPES_MAP
        )

    public val APP_FUNCTION_METADATA: CompileTimeAppFunctionMetadata =
        CompileTimeAppFunctionMetadata(
            id = "note/updateNote/2",
            isEnabledByDefault = true,
            schema =  SCHEMA_METADATA,
            parameters = PARAMETER_METADATA_LIST,
            response = RESPONSE_METADATA,
            components = COMPONENTS_METADATA
        )
  }
}
