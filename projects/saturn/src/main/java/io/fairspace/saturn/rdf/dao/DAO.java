package io.fairspace.saturn.rdf.dao;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Callable;

import com.pivovarit.function.ThrowingBiConsumer;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.rdf.SparqlUtils.generateMetadataIri;

import static com.fasterxml.jackson.databind.type.TypeFactory.rawClass;
import static java.lang.String.format;
import static java.time.Instant.now;
import static java.time.Instant.ofEpochMilli;
import static java.util.Optional.ofNullable;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.rdf.model.ResourceFactory.*;

/**
 * A simple Data Access Object for objects extending io.fairspace.saturn.rdf.dao.PersistentEntity.
 * <p>
 * No lazy loading, no caching, no bytecode manipulation - as simple as it can be.
 * Entity classes must be annotated with @io.fairspace.saturn.rdf.dao.RDFType and have a zero-arguments constructor.
 * Each persistent property must be annotated with @io.fairspace.saturn.rdf.dao.RDFProperty.
 * References to other entities should be stored as org.apache.jena.graph.Node values and can be created using
 * org.apache.jena.graph.NodeFactory.createURI
 * <p>
 * <p>
 * Supported field types:
 * - All primitive types and their object wrappers, e.g. java.lang.Long
 * - java.lang.String
 * - java.time.Instant
 * - org.apache.jena.graph.Node
 * - Collections (descendants of java.util.Collection) of the above types.
 * <p>
 * All collection fields must be initialized on object initialization:
 * private final Set<Node> items = new HashSet<>();
 * <p>
 * Lists (java.util.List) can be used, but order of elements is not preserved.
 * <p>
 * Scalar fields can be marked as required (see @RDFProperty), in that case DAO checks if they are initialized during
 * serialization and deserialization and throws a DAOException in case of violation.
 * <p>
 * For LifecycleAwarePersistentEntity's descendants, DAO automatically updates related fields (dateCreated, etc).
 */
public class DAO {
    private static final String NO_VALUE_ERROR = "No value for required field %s in entity %s";
    private static final String UNINITIALIZED_COLLECTION_ERROR = "An uninitialized collection field %s in class %s";
    private static final String NO_RDF_TYPE_ERROR = "No RDF type specified for %s";
    private static final String CASTING_ERROR = "Cannot cast %s to %s";
    private static final String TOO_MANY_VALUES_ERROR = "More than one value for scalar field %s in resource %s";
    private static final String WRONG_ENTITY_TYPE_ERROR = "Entity %s is not of type %s";

    @Getter
    private final Model model;

    public DAO(Model model) {
        this.model = model;
    }

    /**
     * Writes (creates or updates) an entity.
     * This method can modify the entity passed as an argument. It it has no IRI it will be automatically assigned.
     * This method also updates the relevant fields of LifecycleAwarePersistentEntity
     *
     * @param entity
     * @param <T>
     * @return the entity passed as an argument
     */
    public <T extends PersistentEntity> T write(T entity) {
        return safely(() -> {
            var type = getRdfType(entity.getClass());

            var graph = model.getGraph();

            if (entity instanceof LifecycleAwarePersistentEntity) {
                var basicEntity = (LifecycleAwarePersistentEntity) entity;
                var user = getUserURI();
                basicEntity.setDateModified(now());
                basicEntity.setModifiedBy(user);

                if (entity.getIri() == null
                        || !graph.contains(entity.getIri(), Node.ANY, Node.ANY)
                        || graph.contains(entity.getIri(), FS.dateDeleted.asNode(), Node.ANY)) {
                    basicEntity.setDateCreated(basicEntity.getDateModified());
                    basicEntity.setCreatedBy(user);
                }
            }

            if (entity.getIri() == null) {
                entity.setIri(generateMetadataIri());
            }

            graph.add(Triple.create(entity.getIri(), RDF.type.asNode(), type));

            processFields(entity.getClass(), (field, annotation) -> {
                var propertyNode = createURI(annotation.value());
                var value = field.get(entity);

                if (value == null && annotation.required()) {
                    throw new DAOException(format(NO_VALUE_ERROR, field.getName(), entity.getIri()));
                }

                graph.remove(entity.getIri(), propertyNode, null);

                if (value instanceof Iterable) {
                    ((Iterable<?>) value)
                            .forEach(
                                    item -> graph.add(Triple.create(entity.getIri(), propertyNode, valueToNode(item))));
                } else if (value != null) {
                    graph.add(Triple.create(entity.getIri(), propertyNode, valueToNode(value)));
                }
            });

            return entity;
        });
    }

    /**
     * Reads an entity
     *
     * @param type
     * @param iri
     * @param <T>
     * @return The found entity or null if no entity was found or it was marked as deleted
     */
    public <T extends PersistentEntity> T read(Class<T> type, Node iri) {
        return read(type, iri, false);
    }

    /**
     * Reads an entity
     *
     * @param type
     * @param iri
     * @param showDeleted
     * @param <T>
     * @return The found entity or null if no entity was found or it was marked as deleted and showDeleted is set to false
     */
    public <T extends PersistentEntity> T read(Class<T> type, Node iri, boolean showDeleted) {
        var resource = model.wrapAsResource(iri);
        return (model.containsResource(resource) && (showDeleted || !resource.hasProperty(FS.dateDeleted)))
                ? entityFromResource(type, resource)
                : null;
    }

    /**
     * Deletes an entity
     *
     * @param entity
     */
    public void delete(PersistentEntity entity) {
        delete(entity.getIri());
    }

    /**
     * Deletes an entity
     *
     * @param iri
     */
    public void delete(Node iri) {
        model.removeAll(model.asRDFNode(iri).asResource(), null, null);
    }

    /**
     * Marks an entity as deleted and updates its dateDeleted and deletedBy fields
     *
     * @param entity
     * @return the entity passed as an argument if no entity was found or it was already marked as deleted
     */
    public <T extends LifecycleAwarePersistentEntity> T markAsDeleted(T entity) {
        var existing = (T) read(entity.getClass(), entity.getIri());
        if (existing != null) {
            existing.setDateDeleted(now());
            existing.setDeletedBy(getUserURI());
            return write(existing);
        }
        return null;
    }

    /**
     * Restores an entity previously marked as deleted
     *
     * @param entity
     * @return the entity passed as an argument if no entity was found or it was already marked as deleted
     */
    public <T extends LifecycleAwarePersistentEntity> T restore(T entity) {
        var existing = (T) read(entity.getClass(), entity.getIri(), true);
        if (existing != null) {
            existing.setDateDeleted(null);
            existing.setDeletedBy(null);
            return write(existing);
        }
        return null;
    }

    /**
     * Lists entities of a specific type (except to marked as deleted)
     *
     * @param type
     * @param <T>
     * @return
     */
    public <T extends PersistentEntity> List<T> list(Class<T> type) {
        return list(type, false);
    }

    /**
     * Lists entities of a specific type
     *
     * @param type
     * @param includeDeleted
     * @param <T>
     * @return
     */
    public <T extends PersistentEntity> List<T> list(Class<T> type, boolean includeDeleted) {
        return model.listSubjectsWithProperty(
                        RDF.type, createResource(getRdfType(type).getURI()))
                .filterKeep(r -> includeDeleted || !r.hasProperty(FS.dateDeleted))
                .mapWith(r -> entityFromResource(type, r))
                .toList();
    }

    public static <T extends PersistentEntity> T entityFromResource(Class<T> type, Resource resource) {
        try {
            var typeResource = createResource(getRdfType(type).getURI());
            if (!resource.hasProperty(RDF.type, typeResource)) {
                throw new DAOException(format(WRONG_ENTITY_TYPE_ERROR, resource.getURI(), typeResource.getURI()));
            }
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            var entity = ctor.newInstance();
            entity.setIri(resource.asNode());
            processFields(type, (field, annotation) -> {
                var property = createProperty(annotation.value());

                var stmts = resource.listProperties(property).toList();

                if (Collection.class.isAssignableFrom(field.getType())) {
                    var collection = (Collection) field.get(entity);
                    if (collection == null) {
                        throw new DAOException(format(UNINITIALIZED_COLLECTION_ERROR, field.getName(), type.getName()));
                    }

                    var parameterizedType = (ParameterizedType) field.getGenericType();
                    var valueType = rawClass(parameterizedType.getActualTypeArguments()[0]);

                    stmts.forEach(stmt -> collection.add(nodeToType(stmt.getObject(), valueType)));
                } else {
                    if (stmts.size() > 1) {
                        throw new DAOException(format(TOO_MANY_VALUES_ERROR, field.getName(), resource.getURI()));
                    }

                    if (!stmts.isEmpty()) {
                        var value = nodeToType(stmts.get(0).getObject(), field.getType());
                        field.set(entity, value);
                    } else if (annotation.required()) {
                        throw new DAOException(format(NO_VALUE_ERROR, field.getName(), resource.getURI()));
                    }
                }
            });
            return entity;
        } catch (Exception e) {
            throw new DAOException(e);
        }
    }

    @SneakyThrows
    private static void processFields(Class<?> type, ThrowingBiConsumer<Field, RDFProperty, Exception> action) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                var annotation = field.getAnnotation(RDFProperty.class);
                if (annotation != null) {
                    field.setAccessible(true);
                    action.accept(field, annotation);
                }
            }
        }
    }

    private static Node valueToNode(Object value) {
        if (value instanceof Node) {
            return (Node) value;
        }
        if (value instanceof String || value instanceof Enum) {
            return createStringLiteral(value.toString()).asNode();
        }
        if (value instanceof Instant) {
            var zdt = ZonedDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
            var call = GregorianCalendar.from(zdt);
            return createTypedLiteral(call).asNode();
        }
        return createTypedLiteral(value).asNode();
    }

    private static Node getRdfType(Class<? extends PersistentEntity> type) {
        return ofNullable(type.getAnnotation(RDFType.class))
                .map(annotation -> createURI(annotation.value()))
                .orElseThrow(() -> new DAOException(format(NO_RDF_TYPE_ERROR, type.getName())));
    }

    private static <T> T safely(Callable<T> action) {
        try {
            return action.call();
        } catch (DAOException e) {
            throw e;
        } catch (Exception e) {
            throw new DAOException(e);
        }
    }

    private static Object nodeToType(RDFNode object, Class<?> type) {
        if (type == Node.class) {
            return object.asNode();
        }

        if (object.isLiteral()) {
            var literal = object.asLiteral();
            if (type == String.class) {
                return literal.getString();
            }
            if (type == Double.class || type == double.class) {
                return literal.getDouble();
            }
            if (type == Float.class || type == float.class) {
                return literal.getFloat();
            }
            if (type == Long.class || type == long.class) {
                return literal.getLong();
            }
            if (type == Integer.class || type == int.class) {
                return literal.getInt();
            }
            if (type == Short.class || type == short.class) {
                return literal.getShort();
            }
            if (type == Character.class || type == char.class) {
                return literal.getChar();
            }
            if (type == Byte.class || type == byte.class) {
                return literal.getByte();
            }
            if (type == Boolean.class || type == boolean.class) {
                return literal.getBoolean();
            }
            if (type == Instant.class) {
                return ofEpochMilli(
                        ((XSDDateTime) literal.getValue()).asCalendar().getTimeInMillis());
            }
            if (Enum.class.isAssignableFrom(type)) {
                return Enum.valueOf((Class<Enum>) type, literal.getString());
            }
        }

        throw new DAOException(format(CASTING_ERROR, object, type.getName()));
    }
}
