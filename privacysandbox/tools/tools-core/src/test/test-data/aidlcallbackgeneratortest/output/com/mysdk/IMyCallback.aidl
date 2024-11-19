package com.mysdk;

import com.mysdk.IStringTransactionCallback;

oneway interface IMyCallback {
    void getName(IStringTransactionCallback transactionCallback) = 5091011;
    void onComplete(boolean result) = 9379493;
}