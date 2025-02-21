/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.action.search;

import org.apache.logging.log4j.Logger;
import org.opensearch.action.OriginalIndices;
import org.opensearch.common.Nullable;
import org.opensearch.common.annotation.PublicApi;
import org.opensearch.common.lease.Releasable;
import org.opensearch.common.util.concurrent.AtomicArray;
import org.opensearch.search.SearchPhaseResult;
import org.opensearch.search.SearchShardTarget;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.internal.ShardSearchContextId;
import org.opensearch.search.internal.ShardSearchRequest;
import org.opensearch.transport.Transport;

import java.util.concurrent.Executor;

/**
 * This class provide contextual state and access to resources across multiple search phases.
 *
 * @opensearch.api
 */
@PublicApi(since = "1.0.0")
public interface SearchPhaseContext extends Executor {
    // TODO maybe we can make this concrete later - for now we just implement this in the base class for all initial phases

    /**
     * Returns the total number of shards to the current search across all indices
     */
    int getNumShards();

    /**
     * Returns a logger for this context to prevent each individual phase to create their own logger.
     */
    Logger getLogger();

    /**
     * Returns the currently executing search task
     */
    SearchTask getTask();

    /**
     * Returns the currently executing search request
     */
    SearchRequest getRequest();

    SearchPhase getCurrentPhase();

    /**
     * Builds and sends the final search response back to the user.
     *
     * @param internalSearchResponse the internal search response
     * @param queryResults           the results of the query phase
     */
    void sendSearchResponse(InternalSearchResponse internalSearchResponse, AtomicArray<SearchPhaseResult> queryResults);

    /**
     * Notifies the top-level listener of the provided exception
     */
    void onFailure(Exception e);

    /**
     * This method will communicate a fatal phase failure back to the user. In contrast to a shard failure
     * will this method immediately fail the search request and return the failure to the issuer of the request
     * @param phase the phase that failed
     * @param msg an optional message
     * @param cause the cause of the phase failure
     */
    void onPhaseFailure(SearchPhase phase, String msg, Throwable cause);

    /**
     * This method will record a shard failure for the given shard index. In contrast to a phase failure
     * ({@link #onPhaseFailure(SearchPhase, String, Throwable)}) this method will immediately return to the user but will record
     * a shard failure for the given shard index. This should be called if a shard failure happens after we successfully retrieved
     * a result from that shard in a previous phase.
     */
    void onShardFailure(int shardIndex, @Nullable SearchShardTarget shardTarget, Exception e);

    /**
     * Returns a connection to the node if connected otherwise and {@link org.opensearch.transport.ConnectTransportException} will be
     * thrown.
     */
    Transport.Connection getConnection(String clusterAlias, String nodeId);

    /**
     * Returns the {@link SearchTransportService} to send shard request to other nodes
     */
    SearchTransportService getSearchTransport();

    /**
     * Releases a search context with the given context ID on the node the given connection is connected to.
     * @see org.opensearch.search.query.QuerySearchResult#getContextId()
     * @see org.opensearch.search.fetch.FetchSearchResult#getContextId()
     *
     */
    default void sendReleaseSearchContext(
        ShardSearchContextId contextId,
        Transport.Connection connection,
        OriginalIndices originalIndices
    ) {
        if (connection != null) {
            getSearchTransport().sendFreeContext(connection, contextId, originalIndices);
        }
    }

    /**
     * Builds an request for the initial search phase.
     */
    ShardSearchRequest buildShardSearchRequest(SearchShardIterator shardIt);

    /**
     * Processes the phase transition from on phase to another. This method handles all errors that happen during the initial run execution
     * of the next phase. If there are no successful operations in the context when this method is executed the search is aborted and
     * a response is returned to the user indicating that all shards have failed.
     */
    void executeNextPhase(SearchPhase currentPhase, SearchPhase nextPhase);

    /**
     * Registers a {@link Releasable} that will be closed when the search request finishes or fails.
     */
    void addReleasable(Releasable releasable);
}
