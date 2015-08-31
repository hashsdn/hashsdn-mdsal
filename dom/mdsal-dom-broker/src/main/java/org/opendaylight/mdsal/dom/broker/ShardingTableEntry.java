/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.mdsal.dom.broker;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.opendaylight.yangtools.concepts.Identifiable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ShardingTableEntry implements Identifiable<PathArgument> {
    private static final Logger LOG = LoggerFactory.getLogger(ShardingTableEntry.class);
    // FIXME: We do probably want to adapt map
    private final Map<PathArgument, ShardingTableEntry> children = new HashMap<>();
    private final PathArgument identifier;
    private ShardRegistration<?> registration;

    ShardingTableEntry() {
        identifier = null;
    }

    ShardingTableEntry(final PathArgument identifier) {
        this.identifier = Preconditions.checkNotNull(identifier);
    }

    @Override
    public PathArgument getIdentifier() {
        return identifier;
    }

    public ShardRegistration<?> getRegistration() {
        return registration;
    }

    ShardingTableEntry lookup(final YangInstanceIdentifier id) {
        final Iterator<PathArgument> it = id.getPathArguments().iterator();
        ShardingTableEntry entry = this;

        while (it.hasNext()) {
            final PathArgument a = it.next();
            final ShardingTableEntry child = entry.children.get(a);
            if (child == null) {
                LOG.debug("Lookup of {} stopped at {}", id, a);
                break;
            }

            entry = child;
        }

        return entry;
    }

    void store(final YangInstanceIdentifier id, final ShardRegistration<?> reg) {
        final Iterator<PathArgument> it = id.getPathArguments().iterator();
        ShardingTableEntry entry = this;

        while (it.hasNext()) {
            final PathArgument a = it.next();
            ShardingTableEntry child = entry.children.get(a);
            if (child == null) {
                child = new ShardingTableEntry(a);
                entry.children.put(a, child);
            }
            // TODO: Is this correct? We want to enter child
            entry = child;
        }

        Preconditions.checkState(entry.registration == null);
        entry.registration = reg;
    }

    private boolean remove(final Iterator<PathArgument> it) {
        if (it.hasNext()) {
            final PathArgument arg = it.next();
            final ShardingTableEntry child = children.get(arg);
            if (child != null) {
                if (child.remove(it)) {
                    children.remove(arg);
                }
            } else {
                LOG.warn("Cannot remove non-existent child {}", arg);
            }
        } else {
            /*
             * Iterator is empty, this effectivelly means is table entry to remove registration.
             * FIXME: We probably want to compare registration object to make sure we are removing
             * correct shard.
             */
            registration = null;
        }
        return registration == null && children.isEmpty();
    }

    void remove(final YangInstanceIdentifier id) {
        this.remove(id.getPathArguments().iterator());
    }
}