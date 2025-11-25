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

package androidx.appsearch.localstorage;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accounts.Account;

import androidx.appsearch.app.AppSearchAccount;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.exceptions.AppSearchException;
import androidx.appsearch.localstorage.converter.AccountToProtoConverter;
import androidx.appsearch.localstorage.util.PrefixUtil;
import androidx.collection.ArraySet;

import com.google.android.appsearch.proto.AccountProto;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class AccountStoreTest {
    private static final String NAMESPACE = "namespace";
    private static final String ID = "id";
    private static final String TEST_ACCOUNT_TYPE_1 = "com.example.type1";
    private static final String TEST_ACCOUNT_NAME_1 = "user1@example.com";
    private static final String TEST_ACCOUNT_ID_1 = "accountId1";
    private static final String TEST_ACCOUNT_TYPE_2 = "com.example.type2";
    private static final String TEST_ACCOUNT_NAME_2 = "user2@example.com";
    private static final String TEST_ACCOUNT_ID_2 = "accountId2";
    private static final String TEST_ACCOUNT_TYPE_3 = "com.example.type3";
    private static final String TEST_ACCOUNT_NAME_3 = "user3@example.com";
    private static final String TEST_ACCOUNT_ID_3 = "accountId3";

    private static final String PREFIX = PrefixUtil.createPrefix("package", "database");
    private static final String PREFIXED_SCHEMA_TYPE = PREFIX + "schema";
    private static final String ACCOUNT_PROPERTY_PATH_1 = "accountProperty1";
    private static final String ACCOUNT_PROPERTY_PATH_2 = "nested.accountProperty2";

    private static final Account ACCOUNT_1 = new Account(TEST_ACCOUNT_NAME_1, TEST_ACCOUNT_TYPE_1);
    private static final Account ACCOUNT_2 = new Account(TEST_ACCOUNT_NAME_2, TEST_ACCOUNT_TYPE_2);

    private File mIcingDir;

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        mIcingDir = mTemporaryFolder.newFolder("icing_dir");
    }

    private AccountProto createAccountProto(String type, String name, String id) {
        return AccountToProtoConverter.toAccountProto(
                new AppSearchAccount.Builder(NAMESPACE, ID)
                        .setAccountType(type)
                        .setAccountName(name)
                        .setAccountId(id)
                        .build());
    }

    @Test
    public void testConstructor_fileDoesNotExist() throws Exception {
        // Do not create the account file, simulate a fresh start
        AccountStore store = AccountStore.create(mIcingDir);

        // Store should initialize with an empty set
        assertThat(store.mAppSearchAccountProtos).isEmpty();
        assertThat(store.mFileNeedsPersist).isFalse();
    }

    @Test
    public void testPutAccounts_duplicateAccount() throws Exception {
        AccountStore store = AccountStore.create(mIcingDir);
        assertFalse(store.mFileNeedsPersist);

        // 1. Add a new account (should mutate)
        AccountProto proto1 = createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1,
                TEST_ACCOUNT_ID_1);
        store.putAccounts(Set.of(proto1));
        assertThat(store.mAppSearchAccountProtos).containsExactly(proto1);
        assertTrue(store.mFileNeedsPersist);

        // 2. Persist the change.
        store.persistToDisk();
        assertFalse(store.mFileNeedsPersist);

        // 3. Add the same account again (should NOT mutate)
        store.putAccounts(Set.of(proto1));
        assertFalse(store.mFileNeedsPersist);
    }

    @Test
    public void testPersistenceAcrossStoreInstances() throws Exception {
        // Data Setup
        AccountProto proto1 = createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1,
                TEST_ACCOUNT_ID_1);
        AccountProto proto2 = createAccountProto(TEST_ACCOUNT_TYPE_2, TEST_ACCOUNT_NAME_2,
                TEST_ACCOUNT_ID_2);
        AccountStore store1 = AccountStore.create(mIcingDir);
        assertFalse(store1.mFileNeedsPersist);

        store1.putAccounts(ImmutableSet.of(proto1, proto2));
        assertTrue(store1.mFileNeedsPersist);

        // Verify in-memory state of store1
        assertThat(store1.mAppSearchAccountProtos).containsExactly(proto1, proto2);

        // --- 2. Initialize Store 2 and verify all data doesn't exist
        AccountStore store2 = AccountStore.create(mIcingDir);
        assertThat(store2.mAppSearchAccountProtos).isEmpty();

        // Persist data
        store1.persistToDisk();
        assertFalse(store1.mFileNeedsPersist);

        AccountStore store3 = AccountStore.create(mIcingDir);
        assertThat(store3.mAppSearchAccountProtos).containsExactly(proto1, proto2);
    }

    @Test
    public void testUpdateAccounts_deletion() throws Exception {
        AccountStore store = AccountStore.create(mIcingDir);

        // Initial state: Store knows about Account 1 and Account 2
        AccountProto proto1 = createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1,
                TEST_ACCOUNT_ID_1);
        AccountProto proto2 = createAccountProto(TEST_ACCOUNT_TYPE_2, TEST_ACCOUNT_NAME_2,
                TEST_ACCOUNT_ID_2);

        // USE API to add initial data
        store.putAccounts(ImmutableSet.of(proto1, proto2));
        assertThat(store.mAllExistingAccounts).isNull();
        assertThat(store.mAppSearchAccountProtos).containsExactly(proto1, proto2);
        assertTrue(store.mFileNeedsPersist);

        store.persistToDisk();
        assertFalse(store.mFileNeedsPersist);

        // Delete account 2
        Set<AccountProto> deletedProtos = store.updateAccounts(ImmutableSet.of(ACCOUNT_1),
                Collections.emptyMap());

        // 1. Verify mAppSearchAccountProtos state (Account 2 should be removed)
        assertThat(store.mAllExistingAccounts).containsExactly(ACCOUNT_1);
        assertThat(store.mAppSearchAccountProtos).containsExactly(proto1);
        assertTrue(store.mFileNeedsPersist);
        assertThat(deletedProtos).containsExactly(proto2);
    }

    @Test
    public void testUpdateAccounts_rename() throws Exception {
        AccountStore store = AccountStore.create(mIcingDir);

        // Initial state: Store knows about the old Account 1
        AccountProto oldProto = createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1,
                TEST_ACCOUNT_ID_1);

        // USE API to add initial data
        store.putAccounts(ImmutableSet.of(oldProto));
        assertThat(store.mAppSearchAccountProtos).containsExactly(oldProto);
        assertThat(store.mAllExistingAccounts).isNull();
        assertTrue(store.mFileNeedsPersist);

        // Call persistToDisk to reset mHasMutated to false (and persist initial state)
        store.persistToDisk();
        assertFalse(store.mFileNeedsPersist);

        // System change: Account 1 is renamed
        Account newAccount = new Account("newName", TEST_ACCOUNT_TYPE_1);

        // System state: New name exists, old name is missing
        Set<AccountProto> deletedProtos = store.updateAccounts(ImmutableSet.of(newAccount),
                ImmutableMap.of(ACCOUNT_1,  newAccount));

        // 1. Verify mAppSearchAccountProtos state (Old proto removed, new proto added)
        // Expected new proto
        AccountProto expectedNewProto = oldProto.toBuilder()
                .setAccountName("newName")
                .build();
        assertThat(store.mAppSearchAccountProtos).containsExactly(expectedNewProto);
        assertThat(store.mAllExistingAccounts).containsExactly(newAccount);
        assertTrue(store.mFileNeedsPersist);

        // 2. Verify no accounts were reported as deleted (it was a rename)
        assertThat(deletedProtos).isEmpty();
    }

    @Test
    public void testVerifyAccountExists() throws Exception {

        AccountStore store = AccountStore.create(mIcingDir);
        ArraySet<AccountProto> newAddingAccounts = new ArraySet<>();

        // Setup 1: Define what accounts the system currently has (ACCOUNT_1 and ACCOUNT_2)
        Set<Account> allExistingAccounts = ImmutableSet.of(ACCOUNT_1, ACCOUNT_2);
        store.updateAccounts(allExistingAccounts, Collections.emptyMap());

        // Setup 2: Define which schemas have account properties
        Map<String, Set<String>> pathsMap = ImmutableMap.of(
                PREFIXED_SCHEMA_TYPE, ImmutableSet.of(ACCOUNT_PROPERTY_PATH_1));

        // --- 1. Valid Cases: Account Exists and is added to newAddingAccounts
        AppSearchAccount account1 = new AppSearchAccount.Builder(NAMESPACE, ID)
                .setAccountType(TEST_ACCOUNT_TYPE_1)
                .setAccountId(TEST_ACCOUNT_ID_1)
                .setAccountName(TEST_ACCOUNT_NAME_1)
                .build();
        GenericDocument doc1 = new GenericDocument.Builder<>(NAMESPACE, "doc1", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_1, account1)
                .build();

        store.verifyAccountExists(pathsMap, PREFIX, doc1, newAddingAccounts);
        // Verify the expected proto was created and added
        assertThat(newAddingAccounts).containsExactly(
                createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1, TEST_ACCOUNT_ID_1));

        // --- 2. Valid Cases: Multiple accounts property (ACCOUNT_1 and ACCOUNT_2)
        AppSearchAccount account2 = new AppSearchAccount.Builder(NAMESPACE, ID)
                .setAccountType(TEST_ACCOUNT_TYPE_2)
                .setAccountId(TEST_ACCOUNT_ID_2)
                .setAccountName(TEST_ACCOUNT_NAME_2)
                .build();
        GenericDocument doc2 = new GenericDocument.Builder<>(NAMESPACE, "doc2", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_1, account1, account2)
                .build();

        store.verifyAccountExists(pathsMap, PREFIX, doc2, newAddingAccounts);
        // Verify both expected protos were created and added
        assertThat(newAddingAccounts).containsExactly(
                createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1, TEST_ACCOUNT_ID_1),
                createAccountProto(TEST_ACCOUNT_TYPE_2, TEST_ACCOUNT_NAME_2, TEST_ACCOUNT_ID_2));

        // --- 3. Valid Cases: put nonExist account to non-account property is fine
        AppSearchAccount nonExist = new AppSearchAccount.Builder(NAMESPACE, ID)
                .setAccountType(TEST_ACCOUNT_TYPE_3)
                .setAccountId(TEST_ACCOUNT_ID_3)
                .setAccountName(TEST_ACCOUNT_NAME_3)
                .build();
        GenericDocument doc3 = new GenericDocument.Builder<>(NAMESPACE, "doc3", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_2, nonExist)
                .build();

        store.verifyAccountExists(pathsMap, PREFIX, doc3, newAddingAccounts);
        // Verify nonExist account doesn't added.
        assertThat(newAddingAccounts).containsExactly(
                createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1, TEST_ACCOUNT_ID_1),
                createAccountProto(TEST_ACCOUNT_TYPE_2, TEST_ACCOUNT_NAME_2, TEST_ACCOUNT_ID_2));

        // --- 4. Invalid Cases: Account does not exist in the system
        GenericDocument doc4 = new GenericDocument.Builder<>(NAMESPACE, "doc4", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_1, nonExist)
                .build();
        AppSearchException e = assertThrows(AppSearchException.class,
                () -> store.verifyAccountExists(pathsMap, PREFIX, doc4, newAddingAccounts));
        assertThat(e.getMessage()).containsMatch("doesn't exist");

        // --- 5. Invalid Cases: Account document is missing Account Type field
        AppSearchAccount noTypeAccount = new AppSearchAccount.Builder(NAMESPACE, ID)
                .setAccountId(TEST_ACCOUNT_ID_3)
                .setAccountName(TEST_ACCOUNT_NAME_3)
                .build();
        GenericDocument doc5 = new GenericDocument.Builder<>(NAMESPACE, "doc5", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_1, noTypeAccount)
                .build();
        e = assertThrows(AppSearchException.class,
                () -> store.verifyAccountExists(pathsMap, PREFIX, doc5, newAddingAccounts));
        assertThat(e.getMessage()).containsMatch("account type");

        // --- 6. Invalid Cases:Account document is missing Account Name field
        AppSearchAccount noNameAccount = new AppSearchAccount.Builder(NAMESPACE, ID)
                .setAccountType(TEST_ACCOUNT_TYPE_3)
                .setAccountId(TEST_ACCOUNT_ID_3)
                .build();
        GenericDocument doc6 = new GenericDocument.Builder<>(NAMESPACE, "doc6", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_1, noNameAccount)
                .build();
        e = assertThrows(AppSearchException.class,
                () -> store.verifyAccountExists(pathsMap, PREFIX, doc6, newAddingAccounts));
        assertThat(e.getMessage()).containsMatch("account name");
    }

    @Test
    public void testVerifyAccountExists_skipVerification() throws Exception {
        AccountStore store = AccountStore.create(mIcingDir);
        ArraySet<AccountProto> newAddingAccounts = new ArraySet<>();

        // Only setup account property path and never update existing accounts
        Map<String, Set<String>> pathsMap = ImmutableMap.of(
                PREFIXED_SCHEMA_TYPE, ImmutableSet.of(ACCOUNT_PROPERTY_PATH_1));

        // Document with a non-existent account
        AppSearchAccount nonExist = new AppSearchAccount.Builder(NAMESPACE, ID)
                .setAccountType(TEST_ACCOUNT_TYPE_3)
                .setAccountId(TEST_ACCOUNT_ID_3)
                .setAccountName(TEST_ACCOUNT_NAME_3)
                .build();
        GenericDocument doc1 = new GenericDocument.Builder<>(NAMESPACE, "doc1", "schema")
                .setPropertyDocument(ACCOUNT_PROPERTY_PATH_1, nonExist)
                .build();

        // The verification should be skipped and no exception should be thrown,
        // even though the account doesn't exist.
        store.verifyAccountExists(pathsMap, PREFIX, doc1, newAddingAccounts);

        // New accounts should NOT be added since verification was skipped.
        assertThat(newAddingAccounts).isEmpty();
    }

    @Test
    public void testReset_clearsInMemoryState() throws Exception {
        AccountStore store = AccountStore.create(mIcingDir);

        // Setup initial state in memory
        AccountProto proto1 = createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1,
                TEST_ACCOUNT_ID_1);
        Map<String, Set<String>> pathsMap = ImmutableMap.of(
                PREFIXED_SCHEMA_TYPE, ImmutableSet.of(ACCOUNT_PROPERTY_PATH_1));

        store.putAccounts(ImmutableSet.of(proto1));
        store.mAllExistingAccounts = ImmutableSet.of(ACCOUNT_1); // Simulate update
        assertTrue(store.mFileNeedsPersist);

        // Action: Reset
        store.reset();

        // Verification
        assertThat(store.mAppSearchAccountProtos).isEmpty();
        assertThat(store.mAllExistingAccounts).isNull();
        assertFalse(store.mFileNeedsPersist);
    }

    @Test
    public void testReset_removesFileAndPersistence() throws Exception {
        // 1. Setup and persist data (Store 1)
        AccountProto proto1 = createAccountProto(TEST_ACCOUNT_TYPE_1, TEST_ACCOUNT_NAME_1,
                TEST_ACCOUNT_ID_1);
        AccountStore store1 = AccountStore.create(mIcingDir);

        store1.putAccounts(ImmutableSet.of(proto1));
        store1.persistToDisk();

        // Sanity Check: A new store (Store 2) can read the persisted data
        AccountStore store2 = AccountStore.create(mIcingDir);
        assertThat(store2.mAppSearchAccountProtos).containsExactly(proto1);

        // 2. Action: Reset Store 1 (this should delete the file)
        store1.reset();

        // 3. Verification: Create Store 3. If reset worked, Store 3 should start empty.
        AccountStore store3 = AccountStore.create(mIcingDir);
        assertThat(store3.mAppSearchAccountProtos).isEmpty();
    }
}
