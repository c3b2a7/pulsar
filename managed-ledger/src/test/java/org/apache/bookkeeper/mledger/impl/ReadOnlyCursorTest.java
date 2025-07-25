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
package org.apache.bookkeeper.mledger.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.bookkeeper.mledger.AsyncCallbacks;
import org.apache.bookkeeper.mledger.Entry;
import org.apache.bookkeeper.mledger.ManagedLedger;
import org.apache.bookkeeper.mledger.ManagedLedgerConfig;
import org.apache.bookkeeper.mledger.ManagedLedgerException;
import org.apache.bookkeeper.mledger.ManagedLedgerException.ManagedLedgerNotFoundException;
import org.apache.bookkeeper.mledger.Position;
import org.apache.bookkeeper.mledger.PositionFactory;
import org.apache.bookkeeper.mledger.ReadOnlyCursor;
import org.apache.bookkeeper.test.MockedBookKeeperTestCase;
import org.testng.annotations.Test;

public class ReadOnlyCursorTest extends MockedBookKeeperTestCase {

    @Test
    void notFound() throws Exception {
        try {
            factory.openReadOnlyCursor("notFound", PositionFactory.EARLIEST, new ManagedLedgerConfig());
            fail("Should have failed");
        } catch (ManagedLedgerNotFoundException e) {
            // Expected
        }

        factory.shutdown();
    }

    @Test
    void simple() throws Exception {
        ManagedLedger ledger = factory.open("simple", new ManagedLedgerConfig().setRetentionTime(1, TimeUnit.HOURS));

        int num = 10;

        for (int i = 0; i < num; i++) {
            ledger.addEntry(("entry-" + i).getBytes());
        }

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("simple", PositionFactory.EARLIEST,
                new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());

        List<Entry> entries = cursor.readEntries(num);
        assertEquals(entries.size(), num);

        assertEquals(cursor.getNumberOfEntries(), 0);
        assertFalse(cursor.hasMoreEntries());
        entries.forEach(Entry::release);
        cursor.close();

        // Ensure we can still write to ledger
        for (int i = 0; i < num; i++) {
            ledger.addEntry(("entry-" + i).getBytes());
        }

        // Open a new cursor
        cursor = factory.openReadOnlyCursor("simple", PositionFactory.EARLIEST, new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), 2 * num);
        assertTrue(cursor.hasMoreEntries());

        entries = cursor.readEntries(num);
        assertEquals(entries.size(), num);

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());
        entries.forEach(Entry::release);

        entries = cursor.readEntries(num);
        assertEquals(entries.size(), num);

        assertEquals(cursor.getNumberOfEntries(), 0);
        assertFalse(cursor.hasMoreEntries());
        entries.forEach(Entry::release);

        cursor.close();
    }

    @Test
    void skip() throws Exception {
        ManagedLedger ledger = factory.open("skip",
                new ManagedLedgerConfig()
                        .setMaxEntriesPerLedger(2)
                        .setRetentionTime(1, TimeUnit.HOURS)
                        .setRetentionSizeInMB(-1));

        int num = 10;

        for (int i = 0; i < num; i++) {
            ledger.addEntry(("entry-" + i).getBytes());
        }

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("skip", PositionFactory.EARLIEST, new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());

        cursor.skipEntries(5);

        assertEquals(cursor.getNumberOfEntries(), num - 5);
        assertTrue(cursor.hasMoreEntries());

        cursor.close();
    }

    @Test
    void skipAll() throws Exception {
        ManagedLedger ledger = factory.open("skip-all",
                new ManagedLedgerConfig().setMaxEntriesPerLedger(7).setRetentionTime(1, TimeUnit.HOURS));

        int num = 10;

        for (int i = 0; i < num; i++) {
            ledger.addEntry(("entry-" + i).getBytes());
        }

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("skip-all", PositionFactory.EARLIEST,
                new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());

        cursor.skipEntries(num);

        assertEquals(cursor.getNumberOfEntries(), 0);
        assertFalse(cursor.hasMoreEntries());

        cursor.close();
    }

    @Test
    void skipMultiple() throws Exception {
        ManagedLedger ledger = factory.open("skip",
                new ManagedLedgerConfig()
                        .setMaxEntriesPerLedger(7)
                        .setRetentionTime(1, TimeUnit.HOURS)
                        .setRetentionSizeInMB(-1));

        int num = 30;

        for (int i = 0; i < num; i++) {
            ledger.addEntry(("entry-" + i).getBytes());
        }

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("skip", PositionFactory.EARLIEST, new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());

        cursor.skipEntries(25);

        assertEquals(cursor.getNumberOfEntries(), 5);
        assertTrue(cursor.hasMoreEntries());

        cursor.skipEntries(5);

        assertEquals(cursor.getNumberOfEntries(), 0);
        assertFalse(cursor.hasMoreEntries());

        cursor.close();
    }

    @Test
    void empty() throws Exception {
        factory.open("empty", new ManagedLedgerConfig().setRetentionTime(1, TimeUnit.HOURS));

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("empty", PositionFactory.EARLIEST,
                new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), 0);
        assertFalse(cursor.hasMoreEntries());

        cursor.close();
    }

    @Test
    void specifyStartPosition() throws Exception {
        ManagedLedger ledger = factory.open("simple", new ManagedLedgerConfig().setRetentionTime(1, TimeUnit.HOURS));

        int num = 10;

        for (int i = 0; i < num; i++) {
            ledger.addEntry(("entry-" + i).getBytes());
        }

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("simple", PositionFactory.EARLIEST,
                new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());

        Position readPosition = cursor.getReadPosition();
        cursor = factory.openReadOnlyCursor("simple", readPosition, new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num);
        assertTrue(cursor.hasMoreEntries());

        cursor.skipEntries(5);

        Position newReadPosition = cursor.getReadPosition();
        cursor = factory.openReadOnlyCursor("simple", newReadPosition, new ManagedLedgerConfig());

        assertEquals(cursor.getNumberOfEntries(), num - 5);
        assertTrue(cursor.hasMoreEntries());
    }

    @Test
    void asyncReadEntriesWithSizeAndBytes() throws Exception {
        ManagedLedger ledger = factory.open("simple", new ManagedLedgerConfig().setRetentionTime(1, TimeUnit.HOURS));

        int numberOfEntries = 10;
        List<byte[]> payloads = new ArrayList<>();

        for (int i = 0; i < numberOfEntries; i++) {
            byte[] payload = ("entry-" + i).getBytes();
            ledger.addEntry(payload);
            payloads.add(payload);
        }

        ReadOnlyCursor cursor = factory.openReadOnlyCursor("simple", PositionFactory.EARLIEST,
                new ManagedLedgerConfig());

        int numberOfEntriesToRead = 8;
        CompletableFuture<List<Entry>> future = new CompletableFuture<>();
        cursor.asyncReadEntries(numberOfEntriesToRead, Long.MAX_VALUE, new AsyncCallbacks.ReadEntriesCallback() {
            @Override
            public void readEntriesComplete(List<Entry> entries, Object ctx) {
                future.complete(entries);
            }

            @Override
            public void readEntriesFailed(ManagedLedgerException exception, Object ctx) {
                future.completeExceptionally(exception);
            }
        }, null, PositionFactory.LATEST);
        List<Entry> entries = future.get(5, TimeUnit.SECONDS);
        assertThat(entries).hasSize(numberOfEntriesToRead);
        entries.forEach(Entry::release);
    }
}
