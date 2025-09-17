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

import androidx.room3.compiler.processing.XType
import androidx.room3.ext.AndroidTypeNames
import androidx.room3.parser.ParsedQuery
import androidx.room3.processor.Context
import androidx.room3.solver.QueryResultBinderProvider
import androidx.room3.solver.TypeAdapterExtras
import androidx.room3.solver.query.result.CursorQueryResultBinder
import androidx.room3.solver.query.result.QueryResultBinder

class CursorQueryResultBinderProvider(val context: Context) : QueryResultBinderProvider {
    override fun provide(
        declared: XType,
        query: ParsedQuery,
        extras: TypeAdapterExtras,
    ): QueryResultBinder {
        return CursorQueryResultBinder()
    }

    override fun matches(declared: XType): Boolean =
        declared.typeArguments.isEmpty() && declared.asTypeName() == AndroidTypeNames.CURSOR
}
