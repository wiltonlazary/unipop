package org.unipop.structure;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.unipop.query.mutation.PropertyQuery;
import org.unipop.query.mutation.RemoveQuery;
import org.unipop.schema.element.ElementSchema;

import java.util.*;

public abstract class UniElement implements Element {
    protected String id;
    protected String label;
    protected ElementSchema schema;
    protected UniGraph graph;

    public UniElement(Map<String, Object> properties, ElementSchema schema, UniGraph graph) {
        this.graph = graph;
        this.schema = schema;

        this.id = ObjectUtils.firstNonNull(
                properties.remove(T.id.getAccessor()),
                properties.remove(T.id.toString()),
                UUID.randomUUID())
                .toString();

        this.label = ObjectUtils.firstNonNull(
                properties.remove(T.label.getAccessor()),
                properties.remove(T.label.toString()),
                getDefaultLabel())
                .toString();
    }

    protected abstract Map<String, Property> getPropertiesMap();

    protected abstract String getDefaultLabel();

    protected Property addPropertyLocal(String key, Object value) {
        ElementHelper.validateProperty(key, value);
        Property property = createProperty(key, value);
        getPropertiesMap().put(key, property);
        return property;
    }

    @Override
    public Object id() {
        return this.id;
    }

    @Override
    public String label() {
        return this.label;
    }

    @Override
    public Graph graph() {
        return this.graph;
    }

    @Override
    public Set<String> keys() {
        return this.getPropertiesMap().keySet();
    }

    @Override
    public <V> Property<V> property(final String key) {
        return this.getPropertiesMap().containsKey(key) ? this.getPropertiesMap().get(key) : Property.<V>empty();
    }

    @Override
    public int hashCode() {
        return ElementHelper.hashCode(this);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(final Object object) {
        return ElementHelper.areEqual(this, object);
    }

    protected Iterator propertyIterator(String[] propertyKeys) {
        Map<String, Property> properties = this.getPropertiesMap();

        if (propertyKeys.length > 0)
            return properties.entrySet().stream().filter(entry -> ElementHelper.keyExists(entry.getKey(), propertyKeys)).map(x -> x.getValue()).iterator();

        return properties.values().iterator();
    }

    public void removeProperty(Property property) {
        getPropertiesMap().remove(property.key());
        PropertyQuery<UniElement> propertyQuery = new PropertyQuery<>(this, property, PropertyQuery.Action.Remove, null);
        this.graph.getControllerManager().getControllers(PropertyQuery.PropertyController.class).forEach(controller ->
                controller.property(propertyQuery));
    }

    protected abstract Property createProperty(String key, Object value);

    @Override
    public void remove() {
        RemoveQuery<UniElement> removeQuery = new RemoveQuery<>(Arrays.asList(this), null);
        this.graph.getControllerManager().getControllers(RemoveQuery.RemoveController.class).forEach(controller ->
                controller.remove(removeQuery));
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public UniGraph getGraph() {
        return graph;
    }

    public static <E extends Element> Map<String, Object> fullProperties(E element) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(T.id.getAccessor(), element.id());
        properties.put(T.label.getAccessor(), element.label());
        element.properties().forEachRemaining(property -> properties.put(property.key(), property.value()));

        return properties;
    }

    public ElementSchema getSchema(){
        return this.schema;
    }

    @Override
    public String toString() {
        return "UniElement{" +
                "properties=" + getPropertiesMap() +
                ", id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", graph=" + graph +
                '}';
    }
}
