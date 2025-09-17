/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.room3.solver.binderprovider

import androidx.room3.compiler.processing.XRawType
import androidx.room3.compiler.processing.XType
import androidx.room3.ext.PagingTypeNames
import androidx.room3.parser.ParsedQuery
import androidx.room3.processor.Context
import androidx.room3.processor.ProcessorErrors
import androidx.room3.solver.QueryResultBinderProvider
import androidx.room3.solver.TypeAdapterExtras
import androidx.room3.solver.query.result.ListQueryResultAdapter
import androidx.room3.solver.query.result.PositionalDataSourceQueryResultBinder
import androidx.room3.solver.query.result.QueryResultBinder

class DataSourceQueryResultBinderProvider(val context: Context) : QueryResultBinderProvider {
    private val dataSourceType: XRawType? by lazy {
        context.processingEnv.findType(PagingTypeNames.DATA_SOURCE.canonicalName)?.rawType
    }

    private val positionalDataSourceType: XRawType? by lazy {
        context.processingEnv
            .findType(PagingTypeNames.POSITIONAL_DATA_SOURCE.canonicalName)
            ?.rawType
    }

    override fun provide(
        declared: XType,
        query: ParsedQuery,
        extras: TypeAdapterExtras,
    ): QueryResultBinder {
        if (query.tables.isEmpty()) {
            context.logger.e(ProcessorErrors.OBSERVABLE_QUERY_NOTHING_TO_OBSERVE)
        }
        val typeArg = declared.typeArguments.last()
        val listAdapter =
            context.typeAdapterStore.findRowAdapter(typeArg, query)?.let {
                ListQueryResultAdapter(typeArg, it)
            }
        val tableNames =
            ((listAdapter?.accessedTableNames() ?: emptyList()) + query.tables.map { it.name })
                .toSet()
        return PositionalDataSourceQueryResultBinder(
            listAdapter = listAdapter,
            tableNames = tableNames,
        )
    }

    override fun matches(declared: XType): Boolean {
        if (dataSourceType == null || positionalDataSourceType == null) {
            return false
        }
        if (declared.typeArguments.isEmpty()) {
            return false
        }
        val isDataSource = dataSourceType!!.isAssignableFrom(declared)
        if (!isDataSource) {
            return false
        }
        val isPositional = positionalDataSourceType!!.isAssignableFrom(declared)
        if (!isPositional) {
            context.logger.e(ProcessorErrors.PAGING_SPECIFY_DATA_SOURCE_TYPE)
        }
        return true
    }
}
