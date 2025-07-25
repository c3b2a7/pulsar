/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.io.kafka.connect;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.reflect.AvroDefault;
import org.apache.avro.reflect.Nullable;
import org.apache.kafka.connect.data.Date;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.client.impl.schema.AvroSchema;
import org.apache.pulsar.client.impl.schema.JSONSchema;
import org.apache.pulsar.client.impl.schema.KeyValueSchemaImpl;
import org.apache.pulsar.io.kafka.connect.schema.KafkaConnectData;
import org.apache.pulsar.io.kafka.connect.schema.PulsarSchemaToKafkaSchema;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the conversion of PulsarSchema To KafkaSchema\.
 */
@Slf4j
public class PulsarSchemaToKafkaSchemaTest {

    static final List<String> STRUCT_FIELDS = Lists.newArrayList(
            "field1",
            "field2",
            "field3",
            "byteField",
            "shortField",
            "intField",
            "longField",
            "floatField",
            "doubleField"
        );
    static final List<String> COMPLEX_STRUCT_FIELDS = Lists.newArrayList(
            "stringArr",
            "stringList",
            "structArr",
            "structList",
            "structMap",
            "struct",
            "byteField",
            "shortField",
            "intField",
            "longField",
            "floatField",
            "doubleField",
            "charField",
            "stringField",
            "byteArr",
            "shortArr",
            "intArr",
            "longArr",
            "floatArr",
            "doubleArr",
            "charArr"
        );

    @Data
    @Accessors(chain = true)
    static class StructWithAnnotations {
        int field1;
        @Nullable
        String field2;
        @AvroDefault("1000")
        Long field3;

        @AvroDefault("0")
        byte byteField;
        @AvroDefault("0")
        short shortField;
        @AvroDefault("0")
        int intField;
        @AvroDefault("0")
        long longField;
        @AvroDefault("0")
        float floatField;
        @AvroDefault("0")
        double doubleField;
    }

    @Data
    @Accessors(chain = true)
    static class ComplexStruct {
        List<String> stringList;
        StructWithAnnotations[] structArr;
        List<StructWithAnnotations> structList;
        Map<String, StructWithAnnotations> structMap;
        StructWithAnnotations struct;

        byte byteField;
        short shortField;
        int intField;
        long longField;
        float floatField;
        double doubleField;
        char charField;
        String stringField;

        byte[] byteArr;
        short[] shortArr;
        int[] intArr;
        long[] longArr;
        float[] floatArr;
        double[] doubleArr;
        char[] charArr;
        String[] stringArr;
    }

    @DataProvider(name = "useOptionalPrimitives")
    public static Object[][] useOptionalPrimitives() {
        return new Object[][] {
                {true},
                {false}
        };
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void bytesSchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.BYTES, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.BYTES);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());

        kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.BYTEBUFFER, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.BYTES);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void stringSchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.STRING, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.STRING);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void booleanSchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.BOOL, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.BOOLEAN);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void int8SchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.INT8, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.INT8);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void int16SchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.INT16, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.INT16);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void int32SchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.INT32, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.INT32);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void int64SchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.INT64, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.INT64);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void float32SchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.FLOAT, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.FLOAT32);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void float64SchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.DOUBLE, useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.FLOAT64);
        assertEquals(useOptionalPrimitives, kafkaSchema.isOptional());
    }

    @Test(dataProvider = "useOptionalPrimitives")
    public void kvBytesSchemaTest(boolean useOptionalPrimitives) {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.KV_BYTES(), useOptionalPrimitives);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.MAP);
        assertEquals(kafkaSchema.keySchema().type(), org.apache.kafka.connect.data.Schema.Type.BYTES);
        assertEquals(kafkaSchema.valueSchema().type(), org.apache.kafka.connect.data.Schema.Type.BYTES);
        assertTrue(kafkaSchema.isOptional());

        // key and value are always optional
        assertTrue(kafkaSchema.keySchema().isOptional());
        assertTrue(kafkaSchema.valueSchema().isOptional());
    }

    @Test
    public void kvBytesIntSchemaTests() {
        Schema pulsarKvSchema = KeyValueSchemaImpl.of(Schema.STRING, Schema.INT64);
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(pulsarKvSchema, false);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.MAP);
        assertEquals(kafkaSchema.keySchema().type(), org.apache.kafka.connect.data.Schema.Type.STRING);
        assertEquals(kafkaSchema.valueSchema().type(), org.apache.kafka.connect.data.Schema.Type.INT64);
        assertTrue(kafkaSchema.isOptional());

        // key and value are always optional
        assertTrue(kafkaSchema.keySchema().isOptional());
        assertTrue(kafkaSchema.valueSchema().isOptional());
    }

    @Test
    public void avroSchemaTest() {
        AvroSchema<StructWithAnnotations> pulsarAvroSchema = AvroSchema.of(StructWithAnnotations.class);
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(pulsarAvroSchema, false);
        org.apache.kafka.connect.data.Schema kafkaSchemaOpt =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(pulsarAvroSchema, true);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.STRUCT);
        assertEquals(kafkaSchema.fields().size(), STRUCT_FIELDS.size());
        for (String name: STRUCT_FIELDS) {
            assertEquals(kafkaSchema.field(name).name(), name);
            // set by avro schema
            assertEquals(kafkaSchema.field(name).schema().isOptional(),
                    kafkaSchemaOpt.field(name).schema().isOptional());
        }
    }

    @Test
    public void avroComplexSchemaTest() {
        AvroSchema<ComplexStruct> pulsarAvroSchema = AvroSchema.of(ComplexStruct.class);
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(pulsarAvroSchema, false);
        org.apache.kafka.connect.data.Schema kafkaSchemaOpt =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(pulsarAvroSchema, true);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.STRUCT);
        assertEquals(kafkaSchema.fields().size(), COMPLEX_STRUCT_FIELDS.size());
        for (String name: COMPLEX_STRUCT_FIELDS) {
            assertEquals(kafkaSchema.field(name).name(), name);
            // set by avro schema
            assertEquals(kafkaSchema.field(name).schema().isOptional(),
                    kafkaSchemaOpt.field(name).schema().isOptional());
        }
    }

    @Test
    public void jsonSchemaTest() {
        JSONSchema<StructWithAnnotations> jsonSchema = JSONSchema
                .of(SchemaDefinition.<StructWithAnnotations>builder()
                .withPojo(StructWithAnnotations.class)
                .withAlwaysAllowNull(false)
                .build());
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(jsonSchema, false);
        org.apache.kafka.connect.data.Schema kafkaSchemaOpt =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(jsonSchema, true);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.STRUCT);
        assertEquals(kafkaSchema.fields().size(), STRUCT_FIELDS.size());
        for (String name: STRUCT_FIELDS) {
            assertEquals(kafkaSchema.field(name).name(), name);
            // set by schema
            assertEquals(kafkaSchema.field(name).schema().isOptional(),
                    kafkaSchemaOpt.field(name).schema().isOptional());
        }
    }

    @Test
    public void jsonComplexSchemaTest() {
        JSONSchema<ComplexStruct> jsonSchema = JSONSchema
                .of(SchemaDefinition.<ComplexStruct>builder()
                        .withPojo(ComplexStruct.class)
                        .withAlwaysAllowNull(false)
                        .build());
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(jsonSchema, false);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.STRUCT);
        assertEquals(kafkaSchema.fields().size(), COMPLEX_STRUCT_FIELDS.size());
        for (String name: COMPLEX_STRUCT_FIELDS) {
            assertEquals(kafkaSchema.field(name).name(), name);
            assertFalse(kafkaSchema.field(name).schema().isOptional());
        }

        kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(jsonSchema, true);
        assertEquals(kafkaSchema.type(), org.apache.kafka.connect.data.Schema.Type.STRUCT);
        assertEquals(kafkaSchema.fields().size(), COMPLEX_STRUCT_FIELDS.size());
        for (String name: COMPLEX_STRUCT_FIELDS) {
            assertEquals(kafkaSchema.field(name).name(), name);
            assertFalse(kafkaSchema.field(name).schema().isOptional());

            if (kafkaSchema.field(name).schema().type().isPrimitive()) {
                // false because .withAlwaysAllowNull(false), avroschema values are used
                assertFalse(kafkaSchema.field(name).schema().isOptional(),
                        kafkaSchema.field(name).schema().type().getName());
            }
        }
    }

    @Test
    public void castToKafkaSchemaTest() {
        assertEquals(Byte.class,
                KafkaConnectData.castToKafkaSchema(100L,
                        org.apache.kafka.connect.data.Schema.INT8_SCHEMA).getClass());

        assertEquals(Short.class,
                KafkaConnectData.castToKafkaSchema(100.0d,
                        org.apache.kafka.connect.data.Schema.INT16_SCHEMA).getClass());

        assertEquals(Integer.class,
                KafkaConnectData.castToKafkaSchema((byte) 5,
                        org.apache.kafka.connect.data.Schema.INT32_SCHEMA).getClass());

        assertEquals(Long.class,
                KafkaConnectData.castToKafkaSchema((short) 5,
                        org.apache.kafka.connect.data.Schema.INT64_SCHEMA).getClass());

        assertEquals(Float.class,
                KafkaConnectData.castToKafkaSchema(1.0d,
                        org.apache.kafka.connect.data.Schema.FLOAT32_SCHEMA).getClass());

        assertEquals(Double.class,
                KafkaConnectData.castToKafkaSchema(1.5f,
                        org.apache.kafka.connect.data.Schema.FLOAT64_SCHEMA).getClass());

        assertEquals(Double.class,
                KafkaConnectData.castToKafkaSchema(new BigInteger("100"),
                        org.apache.kafka.connect.data.Schema.FLOAT64_SCHEMA).getClass());
    }

    @Test
    public void dateSchemaTest() {
        org.apache.kafka.connect.data.Schema kafkaSchema =
                PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.DATE, true);
        assertEquals(kafkaSchema.type(), Date.SCHEMA.type());
        assertFalse(kafkaSchema.isOptional());
    }

    // not supported schemas below:
    @Test(expectedExceptions = IllegalStateException.class)
    public void timeSchemaTest() {
        PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.TIME, false);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void timestampSchemaTest() {
        PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.TIMESTAMP, false);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void instantSchemaTest() {
        PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.INSTANT, false);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void localDateSchemaTest() {
        PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.LOCAL_DATE, false);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void localTimeSchemaTest() {
        PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.LOCAL_TIME, false);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void localDatetimeSchemaTest() {
        PulsarSchemaToKafkaSchema.getKafkaConnectSchema(Schema.LOCAL_DATE_TIME, false);
    }

}
