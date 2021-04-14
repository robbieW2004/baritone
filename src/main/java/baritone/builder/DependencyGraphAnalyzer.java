/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.builder;

import baritone.api.utils.BetterBlockPos;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Some initial checks on the schematic
 * <p>
 * The intent is to provide reasonable error messages, which we can do by catching common cases as early as possible
 * <p>
 * So that it's an actual comprehensible error **that tells you where the problem is** instead of just "pathing failed"
 */
public class DependencyGraphAnalyzer {

    public static void prevalidate(PlaceOrderDependencyGraph graph) {
        graph.bounds().forEach(pos -> {
            if (graph.airTreatedAsScaffolding(pos)) {
                // completely fine to, for example, have an air pocket with non-place-against-able stuff all around it
                return;
            }
            for (Face face : Face.VALUES) {
                if (graph.incomingEdge(pos, face)) {
                    return;
                }
            }
            throw new IllegalStateException(BetterBlockPos.fromLong(pos) + " is unplaceable from any side");
        });
    }

    public static void prevalidateExternalToInteriorSearch(PlaceOrderDependencyGraph graph) {
        LongOpenHashSet reachable = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        graph.bounds().forEach(pos -> {
            for (Face face : Face.VALUES) {
                if (graph.incomingEdgePermitExterior(pos, face) && !graph.incomingEdge(pos, face)) {
                    // this block is placeable from the exterior of the schematic!
                    queue.enqueue(pos); // this will intentionally put the top of the schematic at the front
                }
            }
        });
        while (!queue.isEmpty()) {
            long pos = queue.dequeueLong();
            if (reachable.add(pos)) {
                for (Face face : Face.VALUES) {
                    if (graph.outgoingEdge(pos, face)) {
                        queue.enqueueFirst(face.offset(pos));
                    }
                }
            }
        }
        graph.bounds().forEach(pos -> {
            if (graph.airTreatedAsScaffolding(pos)) {
                // same as previous validation
                return;
            }
            if (!reachable.contains(pos)) {
                throw new IllegalStateException(BetterBlockPos.fromLong(pos) + " is placeable, in theory, but in practice there is no valid path from the exterior to it");
            }
        });
    }
}