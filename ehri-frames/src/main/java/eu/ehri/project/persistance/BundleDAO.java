package eu.ehri.project.persistance;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.map.MultiValueMap;
import org.neo4j.graphdb.Transaction;

import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Index;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.VertexFrame;

import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphHelpers;
import eu.ehri.project.exceptions.IdGenerationError;
import eu.ehri.project.exceptions.IndexNotFoundException;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.annotations.Dependent;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.idgen.AccessibleEntityIdGenerator;
import eu.ehri.project.models.idgen.DocumentaryUnitIdGenerator;
import eu.ehri.project.models.idgen.GenericIdGenerator;
import eu.ehri.project.models.idgen.IdGenerator;
import eu.ehri.project.models.utils.ClassUtils;

/**
 * Class responsible for persisting and deleting an EntityBundle<T>, a data
 * structure representing a graph node and its relations to be updated in a
 * single batch.
 * 
 * @param <T>
 */
public class BundleDAO<T extends VertexFrame> {

    private final FramedGraph<Neo4jGraph> graph;
    private final GraphHelpers helpers;
    private final PermissionScope scope;

    /**
     * Constructor with a given scope.
     * 
     * @param graph
     * @param scope
     */
    public BundleDAO(FramedGraph<Neo4jGraph> graph, PermissionScope scope) {
        this.graph = graph;
        this.helpers = new GraphHelpers(graph.getBaseGraph().getRawGraph());
        this.scope = scope;
    }

    /**
     * Constructor with system scope.
     * 
     * @param graph
     */
    public BundleDAO(FramedGraph<Neo4jGraph> graph) {
        this(graph, new SystemScope());
    }

    /**
     * Entry-point for updating a bundle.
     * 
     * @param bundle
     * @return
     * @throws ValidationError
     * @throws IntegrityError
     */
    public T update(EntityBundle<T> bundle) throws ValidationError,
            IntegrityError {
        return graph.frame(updateInner(bundle), bundle.getBundleClass());
    }

    /**
     * Entry-point for updating a bundle.
     * 
     * @param bundle
     * @return
     * @throws ValidationError
     * @throws IndexNotFoundException
     * @throws IntegrityError
     */
    public T createOrUpdate(String key, String value, EntityBundle<T> bundle)
            throws ValidationError, IndexNotFoundException, IntegrityError {
        Index<Vertex> index = helpers.getIndex(bundle.getEntityType(),
                Vertex.class);
        if (index == null)
            throw new ValidationError("Cannot find index or item type: "
                    + bundle.getEntityType());
        Vertex node = null;
        CloseableIterable<Vertex> nodes = index.get(key, value);
        try {
            if (nodes.iterator().hasNext()) {
                node = updateInner(bundle);
            } else {
                node = createInner(bundle);
            }
            return graph.frame(node, bundle.getBundleClass());
        } finally {
            nodes.close();
        }
    }

    public T create(EntityBundle<T> bundle) throws ValidationError,
            IntegrityError {
        return graph.frame(createInner(bundle), bundle.getBundleClass());
    }

    /**
     * Delete a bundle and dependent items, returning the total number of
     * vertices deleted.
     * 
     * @param bundle
     * @return
     * @throws ValidationError
     */
    public Integer delete(EntityBundle<?> bundle) throws ValidationError {
        Transaction tx = graph.getBaseGraph().getRawGraph().beginTx();
        try {
            Integer count = deleteCount(bundle, 0);
            tx.success();
            return count;
        } catch (Exception e) {
            tx.failure();
            throw new RuntimeException(e);
        } finally {
            tx.finish();
        }
    }

    // Helpers

    /**
     * Get the IDs of nodes that terminate a given relationship from a
     * particular source node.
     * 
     * @param src
     * @param direction
     * @param label
     * @return
     */
    private List<Long> getCurrentRelationships(final Vertex src,
            Direction direction, String label) {
        List<Long> out = new LinkedList<Long>();
        for (Vertex end : src.getVertices(direction, label)) {
            out.add((Long) end.getId());
        }
        return out;
    }

    private Integer deleteCount(EntityBundle<?> bundle, Integer count)
            throws ValidationError {
        Integer c = count;
        MultiValueMap fetch = bundle.getRelations();
        List<String> dependents = ClassUtils.getDependentRelations(bundle
                .getBundleClass());
        for (Object key : fetch.keySet()) {
            for (Object obj : fetch.getCollection(key)) {
                // FIXME: Make it so we don't typically do this check for
                // Dependent relations
                if (dependents.contains(key)) {
                    EntityBundle<?> sub = (EntityBundle<?>) obj;
                    c = deleteCount(sub, c);
                }
            }
        }
        if (bundle.id != null) {
            try {
                Index<Vertex> index = helpers.getIndex(bundle.getEntityType(),
                        Vertex.class);
                helpers.deleteVertex(index, bundle.id, bundle.getPropertyKeys());
            } catch (IndexNotFoundException e) {
                // If there's no index, we can do things the simple way...
                graph.removeVertex(graph.getVertex(bundle.id));
            }
            c += 1;
        }
        return c;
    }

    /**
     * Get the direction of a given relationship between two FramedVertex
     * classes.
     * 
     * @param classA
     * @param classB
     * @param rel
     * @return
     */
    private Direction getDirectionOfRelationship(
            Class<? extends VertexFrame> classA,
            Class<? extends VertexFrame> classB, String rel) {
        for (Method method : classA.getMethods()) {
            Dependent dep = method.getAnnotation(Dependent.class);
            if (dep != null) {
                Adjacency adj = method.getAnnotation(Adjacency.class);
                if (adj != null && adj.label().equals(rel)) {
                    return adj.direction();
                }
            }
        }
        // If we get here then something has gone badly wrong, because the
        // correct direction could not be found. Maybe it's better to just
        // ignore saving the dependency in the long run?
        throw new RuntimeException(
                String.format(
                        "Unable to find the direction of relationship between dependent classes with relationship '%s': '%s', '%s'",
                        rel, classA.getName(), classB.getName()));
    }

    private Vertex insertOrUpdate(EntityBundle<T> bundle)
            throws ValidationError, IntegrityError {
        return bundle.getId() == null ? createInner(bundle)
                : updateInner(bundle);
    }

    /**
     * Insert a bundle and save it's dependent items.
     * 
     * @param bundle
     * @return
     * @throws ValidationError
     * @throws IntegrityError
     */
    private Vertex createInner(EntityBundle<T> bundle) throws ValidationError,
            IntegrityError {
        try {
            bundle.validateForInsert();
            Index<Vertex> index = helpers.getOrCreateIndex(
                    bundle.getEntityType(), Vertex.class);
            String id = getIdGenerator(bundle).generateId(
                    bundle.getEntityType(), scope, bundle.getData());
            bundle.setDataValue("__ID", id);
            Vertex node = helpers.createIndexedVertex(bundle.getData(), index,
                    bundle.getPropertyKeys(), bundle.getUniquePropertyKeys());
            saveDependents(node, bundle.getBundleClass(), bundle.getRelations());
            return node;
        } catch (IdGenerationError err) {
            throw new ValidationError(err.getMessage());
        }
    }

    /**
     * Update a bundle and save its dependent items.
     * 
     * @param bundle
     * @return
     * @throws ValidationError
     * @throws IntegrityError
     */
    private Vertex updateInner(EntityBundle<T> bundle) throws ValidationError,
            IntegrityError {
        bundle.validateForUpdate();
        Index<Vertex> index = helpers.getOrCreateIndex(bundle.getEntityType(),
                Vertex.class);
        Vertex node = helpers.updateIndexedVertex(bundle.getId(),
                bundle.getData(), index, bundle.getPropertyKeys(),
                bundle.getUniquePropertyKeys());
        saveDependents(node, bundle.getBundleClass(), bundle.getRelations());
        return node;
    }

    /**
     * Saves the dependent relations within a given bundle. Relations that are
     * not dependent are ignored.
     * 
     * @param master
     * @param cls
     * @param relations
     * @throws ValidationError
     * @throws IntegrityError
     */
    private void saveDependents(Vertex master,
            Class<? extends VertexFrame> cls, MultiValueMap relations)
            throws ValidationError, IntegrityError {
        List<String> dependents = ClassUtils.getDependentRelations(cls);
        Set<Long> existingDependents = new HashSet<Long>();
        Set<Long> refreshedDependents = new HashSet<Long>();

        for (Object key : relations.keySet()) {
            String relation = (String) key;
            if (dependents.contains(relation)) {
                for (Object obj : relations.getCollection(key)) {
                    EntityBundle<T> bundle = (EntityBundle<T>) obj;
                    Vertex child = insertOrUpdate(bundle);
                    refreshedDependents.add((Long) child.getId());
                    Direction direction = getDirectionOfRelationship(cls,
                            bundle.getBundleClass(), relation);

                    // FIXME: Traversing all the current relations here (for
                    // every individual dependent) is very inefficient!
                    List<Long> current = getCurrentRelationships(master,
                            direction, relation);
                    existingDependents.addAll(current);

                    // Create a relation if there isn't one already
                    if (!current.contains(child.getId())) {
                        createChildRelationship(master, child, relation,
                                direction);
                    }
                }
            }
        }
        // Clean up dependent items that have not been saved in the
        // current operation, and are therefore assumed deleted.
        existingDependents.removeAll(refreshedDependents);
        for (Long id : existingDependents) {
            graph.removeVertex(graph.getVertex(id));
        }
    }

    /**
     * Create a
     * 
     * @param master
     * @param child
     * @param label
     * @param direction
     */
    private void createChildRelationship(Vertex master, Vertex child,
            String label, Direction direction) {
        if (direction == Direction.OUT) {
            graph.addEdge(null, master, child, label);
        } else {
            graph.addEdge(null, child, master, label);
        }
    }

    // FIXME: Put this logic somewhere else. When EntityTypes is converted
    // to an Enum we'll be able to associate an ID generator with a
    // particular type, but for now do it the crappy hacky way.
    private IdGenerator getIdGenerator(EntityBundle<T> bundle) {
        if (DocumentaryUnit.class.isAssignableFrom(bundle.getBundleClass())) {
            return new DocumentaryUnitIdGenerator();
        } else if (AccessibleEntityIdGenerator.class.isAssignableFrom(bundle
                .getBundleClass())) {
            return new AccessibleEntityIdGenerator();
        } else {
            return new GenericIdGenerator();
        }
    }
}
