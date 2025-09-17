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
import androidx.room3.solver.query.result.DataSourceFactoryQueryResultBinder
import androidx.room3.solver.query.result.ListQueryResultAdapter
import androidx.room3.solver.query.result.PositionalDataSourceQueryResultBinder
import androidx.room3.solver.query.result.QueryResultBinder

class DataSourceFactoryQueryResultBinderProvider(val context: Context) : QueryResultBinderProvider {
    private val dataSourceFactoryType: XRawType? by lazy {
        context.processingEnv.findType(PagingTypeNames.DATA_SOURCE_FACTORY.canonicalName)?.rawType
    }

    override fun provide(
        declared: XType,
        query: ParsedQuery,
        extras: TypeAdapterExtras,
    ): QueryResultBinder {
        if (query.tables.isEmpty()) {
            context.logger.e(ProcessorErrors.OBSERVABLE_QUERY_NOTHING_TO_OBSERVE)
        }
        val typeArg = declared.typeArguments[1]
        val adapter =
            context.typeAdapterStore.findRowAdapter(typeArg, query)?.let {
                ListQueryResultAdapter(typeArg, it)
            }

        val tableNames =
            ((adapter?.accessedTableNames() ?: emptyList()) + query.tables.map { it.name }).toSet()
        val countedBinder =
            PositionalDataSourceQueryResultBinder(listAdapter = adapter, tableNames = tableNames)
        return DataSourceFactoryQueryResultBinder(countedBinder)
    }

    override fun matches(declared: XType): Boolean =
        declared.typeArguments.size == 2 && isLivePagedList(declared)

    private fun isLivePagedList(declared: XType): Boolean {
        if (dataSourceFactoryType == null) {
            return false
        }
        // we don't want to return paged list unless explicitly requested
        return declared.rawType.isAssignableFrom(dataSourceFactoryType!!)
    }
}
