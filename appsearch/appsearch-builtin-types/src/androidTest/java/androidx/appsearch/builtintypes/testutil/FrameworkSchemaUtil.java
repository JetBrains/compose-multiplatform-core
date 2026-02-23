/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.appsearch.builtintypes.testutil;

import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.AppSearchSchema.BytesPropertyConfig;
import androidx.appsearch.app.AppSearchSchema.LongPropertyConfig;
import androidx.appsearch.app.AppSearchSchema.PropertyConfig;
import androidx.appsearch.app.AppSearchSchema.StringPropertyConfig;

public class FrameworkSchemaUtil {
    // TODO(b/261640992) Temporarily duplicate those two platform schema types for ContactsIndexer
    //  from T. In U, we should consider moving those two in Jetpack, and sync them to Framework
    //  for better and easier testability.
    // Platform ContactPoint schema type
    public static final String CONTACT_POINT_SCHEMA_TYPE = "builtin:ContactPoint";
    public static final String CONTACT_POINT_PROPERTY_LABEL = "label";
    public static final String CONTACT_POINT_PROPERTY_APP_ID = "appId";
    public static final String CONTACT_POINT_PROPERTY_ADDRESS = "address";
    public static final String CONTACT_POINT_PROPERTY_EMAIL = "email";
    public static final String CONTACT_POINT_PROPERTY_TELEPHONE = "telephone";

    // Do not change it!
    // This is the Framework ContactPoint schema type in T for AppSearch, which can't be updated.
    public static final AppSearchSchema CONTACT_POINT_SCHEMA_FOR_T = new AppSearchSchema.Builder(
            CONTACT_POINT_SCHEMA_TYPE)
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_LABEL)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            // appIds
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_APP_ID)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .build())
            // address
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_ADDRESS)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            // email
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_EMAIL)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            // telephone
            .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                    CONTACT_POINT_PROPERTY_TELEPHONE)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                    .setIndexingType(
                            AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build())
            .build();
    public static final String PERSON_SCHEMA_NAME = "builtin:Person";
    public static final String PERSON_PROPERTY_NAME = "name";
    public static final String PERSON_PROPERTY_GIVEN_NAME = "givenName";
    public static final String PERSON_PROPERTY_MIDDLE_NAME = "middleName";
    public static final String PERSON_PROPERTY_FAMILY_NAME = "familyName";
    public static final String PERSON_PROPERTY_EXTERNAL_URI = "externalUri";
    public static final String PERSON_PROPERTY_ADDITIONAL_NAME_TYPES = "additionalNameTypes";
    public static final String PERSON_PROPERTY_ADDITIONAL_NAMES = "additionalNames";
    public static final String PERSON_PROPERTY_IS_IMPORTANT = "isImportant";
    public static final String PERSON_PROPERTY_IS_BOT = "isBot";
    public static final String PERSON_PROPERTY_IMAGE_URI = "imageUri";
    public static final String PERSON_PROPERTY_CONTACT_POINTS = "contactPoints";
    public static final String PERSON_PROPERTY_AFFILIATIONS = "affiliations";
    public static final String PERSON_PROPERTY_RELATIONS = "relations";
    public static final String PERSON_PROPERTY_NOTES = "notes";
    public static final String PERSON_PROPERTY_FINGERPRINT = "fingerprint";

    // Do not change it!
    // This is the Framework Person schema type in T for AppSearch, which can't be updated.
    public static final AppSearchSchema PERSON_SCHEMA_FOR_T =
            new AppSearchSchema.Builder(PERSON_SCHEMA_NAME)
                    // full display name
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(
                                    AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(
                                    AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .build())
                    // given name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_GIVEN_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // middle name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_MIDDLE_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // family name from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_FAMILY_NAME)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // lookup uri from CP2
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_EXTERNAL_URI)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // corresponding name types for the names stored in additional names below.
                    .addProperty(new AppSearchSchema.LongPropertyConfig.Builder(
                            PERSON_PROPERTY_ADDITIONAL_NAME_TYPES)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                            .build())
                    // additional names e.g. nick names and phonetic names.
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_ADDITIONAL_NAMES)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                            .setIndexingType(
                                    AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(
                                    AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .build())
                    // isImportant. It could be used to store isStarred from CP2.
                    .addProperty(new AppSearchSchema.BooleanPropertyConfig.Builder(
                            PERSON_PROPERTY_IS_IMPORTANT)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // isBot
                    .addProperty(new AppSearchSchema.BooleanPropertyConfig.Builder(
                            PERSON_PROPERTY_IS_BOT)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // imageUri
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_IMAGE_URI)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // ContactPoint
                    .addProperty(new AppSearchSchema.DocumentPropertyConfig.Builder(
                            PERSON_PROPERTY_CONTACT_POINTS,
                            CONTACT_POINT_SCHEMA_TYPE)
                            .setShouldIndexNestedProperties(true)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                            .build())
                    // Affiliations
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_AFFILIATIONS)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                            .setIndexingType(
                                    AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(
                                    AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .build())
                    // Relations
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_RELATIONS)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                            .build())
                    // Notes
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_NOTES)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
                            .setIndexingType(
                                    AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(
                                    AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .build())
                    //
                    // Following fields are internal to ContactsIndexer.
                    //
                    // Fingerprint for detecting significant changes
                    .addProperty(new AppSearchSchema.StringPropertyConfig.Builder(
                            PERSON_PROPERTY_FINGERPRINT)
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    .build();

    public static final String MOBILE_APPLICATION_SCHEMA_NAME = "builtin:MobileApplication";
    public static final String MOBILE_APPLICATION_PROPERTY_PACKAGE_NAME = "packageName";
    public static final String MOBILE_APPLICATION_PROPERTY_DISPLAY_NAME = "displayName";
    public static final String MOBILE_APPLICATION_PROPERTY_ALTERNATE_NAMES = "alternateNames";
    public static final String MOBILE_APPLICATION_PROPERTY_ICON_URI = "iconUri";
    public static final String MOBILE_APPLICATION_PROPERTY_UPDATED_TIMESTAMP = "updatedTimestamp";
    public static final String MOBILE_APPLICATION_PROPERTY_CLASS_NAME = "className";
    public static final String MOBILE_APPLICATION_PROPERTY_SHA256_CERTIFICATE = "sha256Certificate";

    public static final AppSearchSchema MOBILE_APPLICATION_SCHEMA =
            new AppSearchSchema.Builder(MOBILE_APPLICATION_SCHEMA_NAME)
                    // packageName
                    .addProperty(new StringPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_PACKAGE_NAME)
                            .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_VERBATIM)
                            .build())
                    // displayName
                    .addProperty(new StringPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_DISPLAY_NAME)
                            .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                            .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .build())
                    // alternateNames
                    .addProperty(new StringPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_ALTERNATE_NAMES)
                            .setCardinality(PropertyConfig.CARDINALITY_REPEATED)
                            .setIndexingType(StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .setTokenizerType(StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .build())
                    // iconUri
                    .addProperty(new StringPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_ICON_URI)
                            .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // sha256Certificate
                    .addProperty(new BytesPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_SHA256_CERTIFICATE)
                            .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // updatedTimestamp
                    .addProperty(new LongPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_UPDATED_TIMESTAMP)
                            .setIndexingType(LongPropertyConfig.INDEXING_TYPE_RANGE)
                            .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    // className
                    .addProperty(new StringPropertyConfig.Builder(
                            MOBILE_APPLICATION_PROPERTY_CLASS_NAME)
                            .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                            .build())
                    .build();

    private FrameworkSchemaUtil() {
    }
}
