package eu.ehri.project.views.impl;

import com.google.common.base.Optional;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.models.base.*;
import eu.ehri.project.persistence.*;
import eu.ehri.project.views.Crud;

/**
 * Views class that handles creating Action objects that provide an audit log
 * for CRUD actions.
 *
 * @param <E>
 * @author michaelb
 */
public class LoggingCrudViews<E extends AccessibleEntity> implements Crud<E> {

    private final ActionManager actionManager;
    private final CrudViews<E> views;
    private final FramedGraph<?> graph;
    private final Class<E> cls;

    /**
     * Scoped Constructor.
     *
     * @param graph The graph
     * @param cls   The entity class to return
     * @param scope The permission scope
     */
    public LoggingCrudViews(FramedGraph<?> graph, Class<E> cls,
            PermissionScope scope) {
        this.graph = graph;
        this.cls = cls;
        actionManager = new ActionManager(graph, scope);
        views = new CrudViews<E>(graph, cls, scope);
    }

    /**
     * Constructor.
     *
     * @param graph The graph
     * @param cls   The entity class to return
     */
    public LoggingCrudViews(FramedGraph<?> graph, Class<E> cls) {
        this(graph, cls, SystemScope.getInstance());
    }

    /**
     * Create a new object of type `E` from the given data, saving an Action log
     * with the default creation message.
     *
     * @param bundle The item's data bundle
     * @param user   The current user
     * @return The created framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     */
    public E create(Bundle bundle, Accessor user) throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError {
        return create(bundle, user, Optional.<String>absent());
    }

    /**
     * Create a new object of type `E` from the given data, saving an Action log
     * with the given log message.
     *
     * @param bundle     The item's data bundle
     * @param user       The current user
     * @param logMessage A log message
     * @return The created framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     */
    public E create(Bundle bundle, Accessor user, Optional<String> logMessage)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError {
        E out = views.create(bundle, user);
        actionManager.logEvent(out, graph.frame(user.asVertex(), Actioner.class),
                EventTypes.creation, logMessage);
        return out;
    }

    /**
     * Create or update a new object of type `E` from the given data, saving an
     * Action log with the default creation message.
     *
     * @param bundle The item's data bundle
     * @param user   The current user
     * @return The created framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     */
    public Mutation<E> createOrUpdate(Bundle bundle, Accessor user)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError {
        return createOrUpdate(bundle, user, Optional.<String>absent());
    }

    /**
     * Create or update a new object of type `E` from the given data, saving an
     * Action log with the given log message.
     *
     * @param bundle     The item's data bundle
     * @param user       The current user
     * @param logMessage A log message
     * @return The created framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     */
    public Mutation<E> createOrUpdate(Bundle bundle, Accessor user, Optional<String> logMessage)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError {
        Mutation<E> out = views.createOrUpdate(bundle, user);
        if (out.updated()) {
            actionManager
                    .logEvent(out.getNode(), graph.frame(user.asVertex(), Actioner.class),
                            EventTypes.modification, logMessage)
                    .createVersion(out.getNode(), out.getPrior().get());
        }
        return out;
    }

    /**
     * Update an object of type `E` from the given data, saving an Action log
     * with the default update message.
     *
     * @param bundle The item's data bundle
     * @param user   The current user
     * @return The updated framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     */
    public Mutation<E> update(Bundle bundle, Accessor user) throws PermissionDenied,
            ValidationError, DeserializationError, IntegrityError {
        return update(bundle, user, Optional.<String>absent());
    }

    /**
     * Update an object of type `E` from the given data, saving an Action log
     * with the given log message.
     *
     * @param bundle     The item's data bundle
     * @param user       The current user
     * @param logMessage A log message
     * @return The updated framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     */
    public Mutation<E> update(Bundle bundle, Accessor user, Optional<String> logMessage)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError {
        try {
            Mutation<E> out = views.update(bundle, user);
            if (!out.unchanged()) {
                actionManager.logEvent(
                        out.getNode(), graph.frame(user.asVertex(), Actioner.class),
                        EventTypes.modification, logMessage)
                        .createVersion(out.getNode(), out.getPrior().get());
            }
            return out;
        } catch (ItemNotFound ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Delete an object bundle, following dependency cascades, saving an Action
     * log with the default deletion message.
     *
     * @param id The item ID
     * @param user The current user
     * @return The number of vertices deleted
     * @throws ItemNotFound
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws SerializationError
     */
    public Integer delete(String id, Accessor user) throws PermissionDenied,
            ValidationError, SerializationError, ItemNotFound {
        return delete(id, user, Optional.<String>absent());
    }

    /**
     * Delete an object bundle, following dependency cascades, saving an Action
     * log with the given deletion message.
     *
     * @param id The item ID
     * @param user The current user
     * @param logMessage A log message
     * @return The number of vertices deleted
     * @throws ItemNotFound
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws SerializationError
     */
    public Integer delete(String id, Accessor user, Optional<String> logMessage)
            throws PermissionDenied, ValidationError, SerializationError, ItemNotFound {
        E item = detail(id, user);
        actionManager
                .logEvent(graph.frame(user.asVertex(), Actioner.class),
                        EventTypes.deletion, logMessage)
                .createVersion(item);
        return views.delete(id, user);
    }

    /**
     * Set the permission scope of the view.
     *
     * @param scope A permission scope
     * @return A new view
     */
    public LoggingCrudViews<E> setScope(PermissionScope scope) {
        return new LoggingCrudViews<E>(graph, cls,
                Optional.fromNullable(scope).or(SystemScope.INSTANCE));
    }

    /**
     * Fetch an item, as a user.
     *
     * @param id The item ID
     * @param user The current user
     * @return The item
     * @throws ItemNotFound
     */
    public E detail(String id, Accessor user) throws ItemNotFound {
        return views.detail(id, user);
    }
}
