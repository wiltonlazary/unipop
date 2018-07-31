package org.unipop.elastic.document;

import com.google.gson.Gson;
import io.searchbox.action.BulkableAction;
import io.searchbox.core.*;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.util.iterator.EmptyIterator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unipop.elastic.common.ElasticClient;
import org.unipop.query.controller.SimpleController;
import org.unipop.query.mutation.AddEdgeQuery;
import org.unipop.query.mutation.AddVertexQuery;
import org.unipop.query.mutation.PropertyQuery;
import org.unipop.query.mutation.RemoveQuery;
import org.unipop.query.search.DeferredVertexQuery;
import org.unipop.query.search.SearchQuery;
import org.unipop.query.search.SearchVertexQuery;
import org.unipop.schema.element.ElementSchema;
import org.unipop.schema.reference.DeferredVertex;
import org.unipop.structure.traversalfilter.TraversalFilter;
import org.unipop.structure.UniEdge;
import org.unipop.structure.UniElement;
import org.unipop.structure.UniGraph;
import org.unipop.structure.UniVertex;
import org.unipop.util.MetricsRunner;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//import org.elasticsearch.index.engine.DocumentAlreadyExistsException;


public class DocumentController implements SimpleController {
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private final ElasticClient client;
    private final UniGraph graph;

    private Set<? extends DocumentVertexSchema> vertexSchemas = new HashSet<>();
    private Set<? extends DocumentEdgeSchema> edgeSchemas = new HashSet<>();

    private TraversalFilter traversalFilter;

    public DocumentController(Set<DocumentSchema> schemas, ElasticClient client, UniGraph graph, TraversalFilter traversalFilter) {
        this.client = client;
        this.graph = graph;

        this.traversalFilter = traversalFilter;

        Set<DocumentSchema> documentSchemas = collectSchemas(schemas);
        this.vertexSchemas = documentSchemas.stream().filter(schema -> schema instanceof DocumentVertexSchema)
                .map(schema -> ((DocumentVertexSchema) schema)).collect(Collectors.toSet());
        this.edgeSchemas = documentSchemas.stream().filter(schema -> schema instanceof DocumentEdgeSchema)
                .map(schema -> ((DocumentEdgeSchema) schema)).collect(Collectors.toSet());

        logger.debug("Instantiated DocumentController: {}", this);
    }

    private Set<DocumentSchema> collectSchemas(Set<? extends ElementSchema> schemas) {
        Set<DocumentSchema> docSchemas = new HashSet<>();

        schemas.forEach(schema -> {
            if (schema instanceof DocumentSchema) {
                docSchemas.add((DocumentSchema) schema);
                Set<DocumentSchema> childSchemas = collectSchemas(schema.getChildSchemas());
                docSchemas.addAll(childSchemas);
            }
        });
        return docSchemas;
    }

    @Override
    public String toString() {
        return "DocumentController{" +
                "client=" + client +
                ", vertexSchemas=" + vertexSchemas +
                ", edgeSchemas=" + edgeSchemas +
                '}';
    }

    //region Query Controller


    @Override
    public <E extends Element> Iterator<E> search(SearchQuery<E> uniQuery) {
        Set<? extends DocumentSchema<E>> schemas = getSchemas(uniQuery.getReturnType())
                .stream().filter(schema -> this.traversalFilter.filter(schema, uniQuery.getTraversal()))
                .map(schema -> ((DocumentSchema<E>) schema))
                .collect(Collectors.toSet());
        Map<DocumentSchema<E>, QueryBuilder> searches = schemas.stream()
                .collect(new SearchCollector<>((schema) -> schema.getSearch(uniQuery)));
        return search(uniQuery, searches);
    }

    @Override
    public Iterator<Edge> search(SearchVertexQuery uniQuery) {
        Map<DocumentEdgeSchema, QueryBuilder> schemas = edgeSchemas.stream()
                .filter(schema -> this.traversalFilter.filter(schema, uniQuery.getTraversal()))
                .collect(new SearchCollector<>((schema) -> schema.getSearch(uniQuery)));
        return search(uniQuery, schemas);
    }

    @Override
    public void fetchProperties(DeferredVertexQuery uniQuery) {
        Map<DocumentVertexSchema, QueryBuilder> schemas = vertexSchemas.stream()
                .filter(schema -> this.traversalFilter.filter(schema, uniQuery.getTraversal()))
                .collect(new SearchCollector<>((schema) -> schema.getSearch(uniQuery)));
        Iterator<Vertex> search = search(uniQuery, schemas);

        Map<Object, DeferredVertex> vertexMap = uniQuery.getVertices().stream()
                .collect(Collectors.toMap(UniElement::id, Function.identity(), (a, b) -> a));
        search.forEachRemaining(newVertex -> {
            DeferredVertex deferredVertex = vertexMap.get(newVertex.id());
            if (deferredVertex != null) deferredVertex.loadProperties(newVertex);
        });
    }

    @Override
    public Edge addEdge(AddEdgeQuery uniQuery) {
        UniEdge edge = new UniEdge(uniQuery.getProperties(), uniQuery.getOutVertex(), uniQuery.getInVertex(), null, graph);
//        try {
        if (index(this.edgeSchemas, edge, true)) return edge;
//        } catch (DocumentAlreadyExistsException ex) {
//            logger.warn("Document already exists in elastic", ex);
//            throw Graph.Exceptions.edgeWithIdAlreadyExists(edge.id());
//        }
        return null;
    }

    @Override
    public Vertex addVertex(AddVertexQuery uniQuery) {
        UniVertex vertex = new UniVertex(uniQuery.getProperties(), null, graph);
//        try {
        if (index(this.vertexSchemas, vertex, true)) return vertex;
//        } catch (DocumentAlreadyExistsException ex) {
//            logger.warn("Document already exists in elastic", ex);
//            throw Graph.Exceptions.vertexWithIdAlreadyExists(vertex.id());
//        }
        return null;
    }

    @Override
    public <E extends Element> void property(PropertyQuery<E> uniQuery) {
        Set<? extends DocumentSchema<E>> schemas = getSchemas(uniQuery.getElement().getClass());
        //updates
        index(schemas, uniQuery.getElement(), false);
    }

    @Override
    public <E extends Element> void remove(RemoveQuery<E> uniQuery) {
        uniQuery.getElements().forEach(element -> {
            Set<? extends DocumentSchema<Element>> schemas = getSchemas(element.getClass());
            delete(schemas, element);
        });
    }

    private <E extends Element> Set<? extends DocumentSchema<E>> getSchemas(Class elementClass) {
        if (Vertex.class.isAssignableFrom(elementClass))
            return (Set<? extends DocumentSchema<E>>) vertexSchemas;
        else return (Set<? extends DocumentSchema<E>>) edgeSchemas;
    }

    //endregion

    //region Elastic Queries

    private void fillChildren(List<MutableMetrics> childMetrics, SearchResult result) {
        if (childMetrics.size() > 0) {
            MutableMetrics child = childMetrics.get(0);
            child.setCount(TraversalMetrics.ELEMENT_COUNT_ID, result.getTotal());
            child.setDuration(Long.parseLong(result.getJsonObject().get("took").toString()), TimeUnit.MILLISECONDS);
        }
    }

    private <E extends Element, S extends DocumentSchema<E>> Pair<S, SearchSourceBuilder> createSearchBuilder(Map.Entry<S, QueryBuilder> kv, SearchQuery<E> query) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(kv.getValue())
                .size(query.getLimit() == -1 ? 10000 : query.getLimit());
        if (query.getPropertyKeys() == null) searchSourceBuilder.fetchSource(true);
        else {
            Set<String> fields = kv.getKey().toFields(query.getPropertyKeys());

            if (fields.size() == 0) searchSourceBuilder.fetchSource(false);
            else searchSourceBuilder.fetchSource(fields.toArray(new String[fields.size()]), null);
        }
        List<Pair<String, Order>> orders = query.getOrders();
        if (orders != null) {
            orders.forEach(order -> {
                Order orderValue = order.getValue1();
                switch (orderValue) {
                    case decr:
                        searchSourceBuilder.sort(kv.getKey()
                                .getFieldByPropertyKey(order.getValue0()), SortOrder.DESC);
                        break;
                    case incr:
                        searchSourceBuilder.sort(kv.getKey()
                                .getFieldByPropertyKey(order.getValue0()), SortOrder.ASC);
                        break;
                    case shuffle:
                        break;
                }
            });
        }
        return Pair.with(kv.getKey(), searchSourceBuilder);
    }

    private <E extends Element, S extends DocumentSchema<E>> Pair<S, Search> createSearch(Pair<S, SearchSourceBuilder> kv, SearchQuery<E> query) {
        Search.Builder builder = new Search.Builder(kv.getValue1().toString().replace("\n", ""))
                .ignoreUnavailable(true).allowNoIndices(true);
        kv.getValue0().getIndex().getIndex(query.getPredicates()).forEach(builder::addIndex);
        return Pair.with(kv.getValue0(), builder.build());
    }

    private <E extends Element, S extends DocumentSchema<E>> Iterator<E> search(SearchQuery<E> query, Map<S, QueryBuilder> schemas) {
//        MetricsRunner metrics = new MetricsRunner(this, query,
//                schemas.keySet().stream().map(s -> ((ElementSchema) s)).collect(Collectors.toList()));

        if (schemas.size() == 0) return EmptyIterator.instance();
        logger.debug("Preparing search. Schemas: {}", schemas);

        client.refresh();


        Map<Search, List<Pair<S, Search>>> groupedQueries = schemas.entrySet().parallelStream().filter(Objects::nonNull)
                .map(kv -> createSearchBuilder(kv, query))
                .map(kv -> createSearch(kv, query))
                .collect(Collectors.groupingBy(Pair::getValue1
                ));

        return groupedQueries.entrySet().stream().flatMap(entry -> {
            Search search = entry.getKey();
            List<S> searchSchemas = entry.getValue().stream().map(Pair::getValue0).collect(Collectors.toList());
            SearchResult results = client.execute(search);
            if (results == null || !results.isSucceeded()) return Stream.empty();
            return searchSchemas.stream().map(s -> s.parseResults(results.getJsonString(), query));
        }).flatMap(Collection::stream).iterator();

    }

    private boolean valid(MultiSearchResult.MultiSearchResponse multiSearchResponse) {
        if (multiSearchResponse.isError) {
            logger.error("failed to execute multiSearch: {}", multiSearchResponse);
            return false;
        }
        return true;
    }

    private <E extends Element> boolean index(Set<? extends DocumentSchema<E>> schemas, E element, boolean create) {
        for (DocumentSchema<E> schema : schemas) {
            BulkableAction<DocumentResult> action = schema.addElement(element, create);
            if (action != null) {
                logger.debug("indexing element with schema: {}, element: {}, index: {}, client: {}", schema, element, action, client);
                client.bulk(element, action);
                return true;
            }
        }
        return false;
    }

    private <E extends Element> void delete(Set<? extends DocumentSchema<E>> schemas, E element) {
        for (DocumentSchema<E> schema : schemas) {
            Delete.Builder delete = schema.delete(element);
            if (delete != null) {
                logger.debug("deleting element with schema: {}, element: {}, client: {}", schema, element, client);
                client.bulk(element, delete.build());
            }
        }
    }

    //endregion

    private class SearchCollector<K extends DocumentSchema, V extends QueryBuilder> implements Collector<K, Map<K, V>, Map<K, V>> {

        private final Function<? super K, ? extends V> valueMapper;

        private SearchCollector(Function<? super K, ? extends V> valueMapper) {
            this.valueMapper = valueMapper;
        }

        @Override
        public Supplier<Map<K, V>> supplier() {
            return HashMap<K, V>::new;
        }

        @Override
        public BiConsumer<Map<K, V>, K> accumulator() {
            return (map, t) -> {
                V value = valueMapper.apply(t);
                if (value != null) map.put(t, value);
            };
        }

        @Override
        public BinaryOperator<Map<K, V>> combiner() {
            return (map1, map2) -> {
                map1.putAll(map2);
                return map1;
            };
        }

        @Override
        public Function<Map<K, V>, Map<K, V>> finisher() {
            return m -> m;
        }

        @Override
        public Set<Collector.Characteristics> characteristics() {
            return EnumSet.of(Collector.Characteristics.IDENTITY_FINISH);
        }
    }
}
