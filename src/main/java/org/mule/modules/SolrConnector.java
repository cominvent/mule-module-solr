/*
 *    Copyright 2013 Juan Alberto López Cavallotti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mule.modules;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthScope;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.mule.api.ConnectionException;
import org.mule.api.ConnectionExceptionCode;
import org.mule.api.annotations.*;
import org.mule.api.annotations.display.FriendlyName;
import org.mule.api.annotations.display.Placement;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.mule.api.annotations.param.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URI;
import java.util.*;


/**
 * Module for Apache Solr Integration, it is based on the SolrJ Java Client API and allows interaction
 * with Apache Solr standalone servers.
 *
 * {@sample.xml ../../../doc/solr-connector.xml.sample solr:config}
 *
 * @author Juan Alberto López Cavallotti
 */
@Connector(name = "solr", schemaVersion = "1.0.0", friendlyName = "Solr", minMuleVersion = "3.3.0")
public class SolrConnector {

    private static final Logger logger = LoggerFactory.getLogger(SolrConnector.class);

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    //the SolrServer connection
    private SolrServer server;

    /**
     * The URL of the Solr server to connect to. To define the solr core to use, specify it here, otherwise the default
     * core will be used.
     */
    @Configurable
    @Optional
    @Default("http://localhost:8983/solr")
    @Placement(order = 1, group = "General Settings", tab = "")
    private String serverUrl;

    /**
     * The username for Solr's http basic authentication. This connector does not allow empty user names.
     */
    @Configurable
    @Optional
    @Placement(order = 2, group = "General Settings", tab = "") @FriendlyName("Basic Auth Username")
    private String username;


    /**
     * The password for Solr's http basic authentication. This connector does not allow empty passwords.
     */
    @Configurable
    @Optional
    @Placement(order = 3, group = "General Settings", tab = "") @FriendlyName("Basic Auth Password")
    private String password;

    /**
     * Connect to the Solr Server using commons http client gateway.
     */
    @Connect
    public synchronized void connect() throws ConnectionException {
        try {
            HttpSolrServer httpClientServer = new HttpSolrServer(serverUrl);


            //if there are credentials, then set them.
            if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {

                //set BASIC authentication on the underlying HTTP client.
                URI uri = new URI(serverUrl);
                AuthScope scope = new AuthScope(uri.getHost(), uri.getPort());
                DefaultHttpClient defaultClient = (DefaultHttpClient) httpClientServer.getHttpClient();
                defaultClient.getCredentialsProvider().setCredentials(scope, new UsernamePasswordCredentials(username, password));

            }


            this.server = httpClientServer;
        } catch (URISyntaxException ex) {
            logger.error("The url: " + serverUrl + " is malformed.");
            throw new ConnectionException(ConnectionExceptionCode.UNKNOWN, ex.getMessage(), "Url is not properly written", ex);
        } catch (Exception ex) {
            throw new ConnectionException(ConnectionExceptionCode.UNKNOWN, ex.getMessage(), "Could not connect to solr", ex);
        }
    }

    /**
     * Identify the connection.
     *
     * @return null, not used at this time.
     */
    @ConnectionIdentifier
    public String connectionIdentifier() {
        return null;
    }

    /**
     * Disconnect from the server, nothing special needed at this time.
     */
    @Disconnect
    public synchronized void disconnect() {
        if (server == null) {
            return;
        }
        //do something to disconnect.
        server = null;
    }

    /**
     * Validate the connection by sending a ping request.
     *
     * @return true if the ping call succeeds, false otherwise.
     */
    @ValidateConnection
    public boolean isConnected() {
        if (server == null) {
            return false;
        }

        try {
            SolrPingResponse response = server.ping();

            if (logger.isDebugEnabled()) {
                logger.debug("Pinged the server, response time is: " + response.getQTime());
            }

            if (response.getQTime() > 0) {
                return true;
            }

        } catch (Exception ex) {
            logger.error("Got exception while trying to Ping Server", ex);
            return false;
        }
        //default answer
        return false;
    }

    /**
     * Submit a query to the server and get the results.
     * <p/>
     * {@sample.xml ../../../doc/solr-connector.xml.sample solr:query}
     *
     * @param q       this is the query string called 'q' using solr's nomenclature, normally this has the form of
     *                <em>field</em>:<em>value</em> or just <em>value</em> for querying the default field. Please take a look
     *                at solr's documentation for info on how to write queries.
     * @param handler which handler to use when querying.
     * @param highlightField The field on which to highlight search results.
     * @param highlightSnippets The number of highlight snippets per result.
     * @param facetFields A list of fields for a faceted query. If not null, will enable faceted search.
     * @param facetLimit The facet limit of the query.
     * @param facetMinCount The facet minimum count of the query.
     * @param parameters These parameters will be added to the query.
     * @param filterQueries A list of queries to filter the results.
     * @param sortFields A list of fields (with sorting criteria) in which the results will be sorted. Sorting criteria
     *                   values could be only either <em>asc</em> or <em>desc</em>.
     * @return a {@link QueryResponse QueryResponse} object with the search results.
     * @throws SolrModuleException This exception wraps exceptions thrown when querying the server fails.
     */
    @Processor
    public QueryResponse query(@FriendlyName("Query") String q,
                               @Optional @Placement(group = "Request", order = 0)@Default("/select") String handler,
                               @Optional @Placement(group = "Highlighting") String highlightField,
                               @Optional @Placement(group = "Highlighting") @Default("1") int highlightSnippets,
                               @Optional @Placement(group = "Faceting") @FriendlyName("Facet Fields") List<String> facetFields,
                               @Optional @Placement(group = "Faceting") @Default("8") int facetLimit,
                               @Optional @Placement(group = "Faceting") @Default("1") int facetMinCount,
                               @Optional @Placement(group = "Query Parameters") @FriendlyName("Additional Prameters") Map<String, String> parameters,
                               @Optional @Placement(group = "Filter Queries") @FriendlyName("Filter Queries") List<String> filterQueries,
                               @Optional @Placement(group = "Sort Fields") @FriendlyName("Sort Fields") Map<String, SolrQuery.ORDER> sortFields) throws SolrModuleException {

        SolrQuery query = new SolrQuery(q);
        if ( null!=handler && ! handler.equals("/select") ) {
            query.setQueryType(handler);
        }

        applyHighlightingLogic(query, highlightField, highlightSnippets);
        applyFacetingLogic(query, facetFields, facetLimit, facetMinCount);

        //check for parameters
        if (parameters == null) {
            parameters = Collections.EMPTY_MAP;
        }

        //add the additional parameters
        for(String key : parameters.keySet()) {
            query.setParam(key, parameters.get(key));
        }

        //check for filter queries
        if (filterQueries == null) {
            filterQueries = Collections.EMPTY_LIST;
        }

        query.addFilterQuery(filterQueries.toArray(EMPTY_STRING_ARRAY));

        //add order queries
        if (sortFields == null) {
            sortFields = Collections.EMPTY_MAP;
        }

        for(String key : sortFields.keySet()) {
            query.addSortField(key, sortFields.get(key));
        }

        //finally query the server
        try {

            return server.query(query);

        } catch (SolrServerException ex) {
            logger.error("Got server exception while trying to query", ex);
            throw new SolrModuleException("Got server exception while trying to query", ex);
        }
    }

    private void applyFacetingLogic(SolrQuery query, List<String> facetFields, int facetLimit, int facetMinCount) {
        if (facetFields == null || facetFields.isEmpty()) {
            logger.debug("Faceting is disabled for this query...");
            return;
        }

        query.setFacet(true);
        query.setFacetLimit(facetLimit);
        query.setFacetMinCount(facetMinCount);
        query.addFacetField(facetFields.toArray(EMPTY_STRING_ARRAY));
    }

    private void applyHighlightingLogic(SolrQuery query, String highlightField, int highlightSnippets) {

        if (highlightField == null) {
            logger.debug("Highlighting is disabled for this query...");
            return;
        }

        query.setHighlight(true);
        query.setHighlightSnippets(highlightSnippets);
        query.setParam("hl.fl", highlightField);
    }

    /**
     * Delete elements by specifying a query, if you wish to delete everything use <em>*:*</em> as the query parameter.
     *
     * {@sample.xml ../../../doc/solr-connector.xml.sample solr:delete-by-query}
     *
     * @param q the query of which results will be deleted.
     * @return the server response object.
     * @throws SolrModuleException This exception wraps the exceptions thrown by the client.
     */
    @Processor
    public UpdateResponse deleteByQuery(@FriendlyName("Query") String q) throws SolrModuleException {
        try {
            return server.deleteByQuery(q);
        } catch (SolrServerException ex) {
            throw new SolrModuleException("Got Server exception while deleting by query", ex);
        } catch (IOException ex) {
            throw new SolrModuleException("Got IOException while deleting by query", ex);
        }
    }


    /**
     * Delete a specific element on the index by ID.
     *
     * {@sample.xml ../../../doc/solr-connector.xml.sample solr:delete-by-id}
     *
     * @param id the ID of the element to delete.
     * @return the server response object.
     * @throws SolrModuleException This exception wraps the exceptions thrown by the client.
     */
    @Processor
    public UpdateResponse deleteById(String id) throws SolrModuleException {

        try {
            return server.deleteById(id);
        } catch (SolrServerException ex) {
            throw new SolrModuleException("Got Server exception while deleting by ID", ex);
        } catch (IOException ex) {
            throw new SolrModuleException("Got IOException while deleting by ID", ex);
        }
    }


    /**
     * Index a simple pojo or a collection of pojo's and then, if everything goes well, commit the results to solr.
     * {@sample.xml ../../../doc/solr-connector.xml.sample solr:index-pojo}
     * @param payload The pojo or collection of pojos to send to the solr server.
     * @return the API response when committing the update.
     * @throws SolrModuleException This exception wraps exceptions thrown by the client.
     */
    @Processor
    public UpdateResponse indexPojo(@Payload Object payload) throws SolrModuleException {


        if (payload == null) {
            logger.debug("Ignored request to index a Null Pojo");
            return null;
        }

        try {

            if (payload instanceof Collection) {

                if (logger.isDebugEnabled()) logger.debug("Indexing beans collection ...");
                server.addBeans((Collection) payload);

            } else {
                if (logger.isDebugEnabled()) logger.debug("Indexing a simple pojo ...");
                server.addBean(payload);
            }

            return server.commit();

        } catch (SolrServerException ex) {
            rollbackUpdates("Got server error while trying to index pojo(s)", ex);
            return null; //unreachable but the compiler complains :)
        } catch (IOException ex) {
            rollbackUpdates("Got IOException while trying to index pojo(s) ", ex);
            return null; //unreachable but the compiler complains :)
        }
    }


    /**
     * Index a simple document or a collection of documents and then, if everything goes well, commit the results to solr.
     * {@sample.xml ../../../doc/solr-connector.xml.sample solr:index}
     * @param payload The document or collection of documents to send to the solr server.
     * @return the API response when committing the update.
     * @throws SolrModuleException This exception wraps exceptions thrown by the client.
     */
    @Processor
    public UpdateResponse index(@Payload Object payload) throws SolrModuleException {

        if (payload == null) {
            logger.debug("Ignored request to index a Null Object");
            return null;
        }

        try {
            if (payload instanceof Collection) {
                server.add((Collection<SolrInputDocument>)payload);
            } else {
                server.add((SolrInputDocument)payload);
            }
            return server.commit();

        } catch (SolrServerException ex) {
            rollbackUpdates("Got server error while trying to index document(s)", ex);
            return null; //unreachable but the compiler complains :)
        } catch (IOException ex) {
            rollbackUpdates("Got IOException while trying to index document(s) ", ex);
            return null; //unreachable but the compiler complains :)
        }
    }

    /**
     * Convert the information of a message to an indexable {@link SolrInputDocument}.
     * {@sample.xml ../../../doc/solr-connector.xml.sample solr:message-to-input-document-transformer}
     * @param fields A map which keys are field names and which values are the actual values for these fields.
     * @return a SolrInputDocument when the fields parameter is not null or empty, it will return null otherwise.
     */
    @Transformer(sourceTypes = java.util.Map.class)
    public static SolrInputDocument messageToInputDocumentTransformer(Map<String, Object> fields) {

        if (fields == null || fields.isEmpty()) {
            logger.debug("Transforming null or empty map to a null input document...");
            return null;
        }

        SolrInputDocument document = new SolrInputDocument();

        for(String key : fields.keySet()) {
             document.setField(key, fields.get(key));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Transformed " + fields.toString() + " into " + document.toString());
        }

        return document;
    }

    private void rollbackUpdates(String message, Exception cause) throws SolrModuleException {
        try {
            logger.error(message, cause);
            server.rollback();
            throw new SolrModuleException(message, cause);
        } catch (Exception ex) {
            message = "Could not rollback an update";
            logger.error(message, ex);
            throw new SolrModuleException(message, ex);
        }
    }

    ///////////////////////////// GETTERS AND SETTERS /////////////////////////////////

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
