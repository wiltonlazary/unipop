package org.unipop.elastic.document.schema;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.elasticsearch.index.query.QueryBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.unipop.elastic.common.ElasticClient;
import org.unipop.elastic.document.DocumentVertexSchema;
import org.unipop.elastic.document.schema.nested.NestedEdgeSchema;
import org.unipop.elastic.document.schema.property.IndexPropertySchema;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.search.DeferredVertexQuery;
import org.unipop.schema.element.EdgeSchema;
import org.unipop.schema.element.ElementSchema;
import org.unipop.structure.UniGraph;
import org.unipop.structure.UniVertex;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.unipop.util.ConversionUtils.getList;

public class DocVertexSchema extends AbstractDocSchema<Vertex> implements DocumentVertexSchema {
    protected Set<ElementSchema> edgeSchemas = new HashSet<>();

    public DocVertexSchema(JSONObject configuration, ElasticClient client, UniGraph graph) throws JSONException {
        super(configuration, client, graph);

        for(JSONObject edgeJson : getList(json, "edges")) {
            EdgeSchema docEdgeSchema = getEdgeSchema(edgeJson);
            edgeSchemas.add(docEdgeSchema);
        }
    }

    public DocVertexSchema(JSONObject configuration, ElasticClient client, UniGraph graph, IndexPropertySchema index) throws JSONException {
        super(configuration, client, graph);

        this.index = index;

        for(JSONObject edgeJson : getList(json, "edges")) {
            EdgeSchema docEdgeSchema = getEdgeSchema(edgeJson);
            edgeSchemas.add(docEdgeSchema);
        }
    }

    private EdgeSchema getEdgeSchema(JSONObject edgeJson) throws JSONException {
        String path = edgeJson.optString("path", null);
        Direction direction = Direction.valueOf(edgeJson.optString("direction"));

        if(path == null) return new InnerEdgeSchema(this, direction, index, type, edgeJson, client, graph);
        return new NestedEdgeSchema(this, direction, index, type, path, edgeJson, client, graph);
    }

    @Override
    public QueryBuilder getSearch(DeferredVertexQuery query) {
        PredicatesHolder predicatesHolder = this.toPredicates(query.getVertices());
        QueryBuilder queryBuilder = createQueryBuilder(predicatesHolder);
        return queryBuilder;
    }

    @Override
    public Vertex createElement(Map<String, Object> fields) {
        Map<String, Object> properties = getProperties(fields);
        if(properties == null) return null;
        return new UniVertex(properties, this, graph);
    }

    @Override
    public Set<ElementSchema> getChildSchemas() {
        return this.edgeSchemas;
    }

    @Override
    public String toString() {
        return "DocVertexSchema{" +
                "index='" + index + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
