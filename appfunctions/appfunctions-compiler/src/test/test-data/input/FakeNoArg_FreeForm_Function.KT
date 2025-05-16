package com.testdata

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext

class FakeNoArg {
    @AppFunction fun noArg(appFunctionContext: AppFunctionContext) {}
}
