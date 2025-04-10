package com.example.appsearch;

import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.DocumentClassFactory;
import androidx.appsearch.app.DocumentClassMappingContext;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.exceptions.AppSearchException;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.appsearch.compiler.AppSearchCompiler")
public final class $$__AppSearch__KotlinGift implements DocumentClassFactory<KotlinGift> {
  public static final String SCHEMA_NAME = "KotlinGift";

  @Override
  public String getSchemaName() {
    return SCHEMA_NAME;
  }

  @Override
  public AppSearchSchema getSchema() throws AppSearchException {
    return new AppSearchSchema.Builder(SCHEMA_NAME)
          .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("nonNullList")
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
            .setJoinableValueType(AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_NONE)
            .build())
          .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("nullableList")
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REPEATED)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
            .setJoinableValueType(AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_NONE)
            .build())
          .addProperty(new AppSearchSchema.BooleanPropertyConfig.Builder("nonNullBoolean")
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .build())
          .addProperty(new AppSearchSchema.BooleanPropertyConfig.Builder("nullableBoolean")
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .build())
          .build();
  }

  @Override
  public List<Class<?>> getDependencyDocumentClasses() throws AppSearchException {
    return Collections.emptyList();
  }

  @Override
  public GenericDocument toGenericDocument(KotlinGift document) throws AppSearchException {
    GenericDocument.Builder<?> builder =
        new GenericDocument.Builder<>(document.getNamespace(), document.getId(), SCHEMA_NAME);
    List<String> nonNullListCopy = document.getNonNullList();
    if (nonNullListCopy != null) {
      String[] nonNullListConv = nonNullListCopy.toArray(new String[0]);
      builder.setPropertyString("nonNullList", nonNullListConv);
    }
    List<String> nullableListCopy = document.getNullableList();
    if (nullableListCopy != null) {
      String[] nullableListConv = nullableListCopy.toArray(new String[0]);
      builder.setPropertyString("nullableList", nullableListConv);
    }
    builder.setPropertyBoolean("nonNullBoolean", document.getNonNullBoolean());
    Boolean nullableBooleanCopy = document.getNullableBoolean();
    if (nullableBooleanCopy != null) {
      builder.setPropertyBoolean("nullableBoolean", nullableBooleanCopy);
    }
    return builder.build();
  }

  @Override
  public KotlinGift fromGenericDocument(GenericDocument genericDoc,
      DocumentClassMappingContext documentClassMappingContext) throws AppSearchException {
    String namespaceConv = genericDoc.getNamespace();
    String idConv = genericDoc.getId();
    String[] nonNullListCopy = genericDoc.getPropertyStringArray("nonNullList");
    List<String> nonNullListConv = Collections.emptyList();
    if (nonNullListCopy != null) {
      nonNullListConv = Arrays.asList(nonNullListCopy);
    }
    String[] nullableListCopy = genericDoc.getPropertyStringArray("nullableList");
    List<String> nullableListConv = null;
    if (nullableListCopy != null) {
      nullableListConv = Arrays.asList(nullableListCopy);
    }
    boolean nonNullBooleanConv = genericDoc.getPropertyBoolean("nonNullBoolean");
    boolean[] nullableBooleanCopy = genericDoc.getPropertyBooleanArray("nullableBoolean");
    Boolean nullableBooleanConv = null;
    if (nullableBooleanCopy != null && nullableBooleanCopy.length != 0) {
      nullableBooleanConv = nullableBooleanCopy[0];
    }
    KotlinGift document = new KotlinGift(namespaceConv, idConv, nonNullListConv, nullableListConv, nonNullBooleanConv, nullableBooleanConv);
    return document;
  }
}
