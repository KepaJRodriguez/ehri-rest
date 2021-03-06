package eu.ehri.project.views;

import com.google.common.base.Optional;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.base.*;
import eu.ehri.project.persistence.*;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class DescriptionViews <E extends DescribedEntity> {

    private final FramedGraph<?> graph;
    private final GraphManager manager;
    private final Crud<E> crud;
    private final ViewHelper helper;
    private final Serializer serializer;
    private final ActionManager actionManager;

    public DescriptionViews(FramedGraph<?> graph, Class<E> parentClass) {
        this.graph = graph;
        this.manager = GraphManagerFactory.getInstance(graph);
        this.crud = ViewFactory.getCrudNoLogging(graph, parentClass);
        this.helper = new ViewHelper(graph);
        this.serializer = new Serializer.Builder(graph).dependentOnly().build();
        this.actionManager = new ActionManager(graph);
    }

    public Integer delete(String parentId, String id, Accessor user, Optional<String> logMessage)
            throws ItemNotFound, PermissionDenied, SerializationError {
        E parent = crud.detail(parentId, user);
        Description desc = manager.getFrame(id, Description.class);
        if (!parent.equals(desc.getDescribedEntity())) {
            throw new PermissionDenied("Given description does not belong to its parent item");
        }
        helper.checkEntityPermission(parent, user, PermissionType.UPDATE);
        actionManager.setScope(parent)
                .logEvent(parent, manager.cast(user, Actioner.class),
                        EventTypes.deleteDependent, logMessage)
                .createVersion(desc);
        return getPersister(parent).delete(serializer.vertexFrameToBundle(desc));
    }

    public <T extends Frame> T create(String parentId, Bundle data,
            Class<T> descriptionClass, Accessor user, Optional<String> logMessage)
            throws ItemNotFound, PermissionDenied, ValidationError {
        E parent = crud.detail(parentId, user);
        helper.checkEntityPermission(parent, user, PermissionType.UPDATE);
        T out = getPersister(parent).create(data, descriptionClass);
        actionManager.setScope(parent).logEvent(parent,
                manager.cast(user, Actioner.class),
                EventTypes.createDependent, logMessage);
        return out;
    }

    public <T extends Frame> Mutation<T> update(String parentId, Bundle data,
                Class<T> descriptionClass, Accessor user, Optional<String> logMessage)
            throws ItemNotFound, PermissionDenied, ValidationError {
        E parent = crud.detail(parentId, user);
        helper.checkEntityPermission(parent, user, PermissionType.UPDATE);
        Mutation<T> out = getPersister(parent).update(data, descriptionClass);
        if (!out.unchanged()) {
            actionManager.setScope(parent)
                    .logEvent(parent, manager.cast(user, Actioner.class),
                            EventTypes.modifyDependent, logMessage)
                    .createVersion(out.getNode(), out.getPrior().get());
        }
        return out;
    }

    // Helpers
    private BundleDAO getPersister(PermissionScope scope) {
        return new BundleDAO(graph, scope.idPath());
    }
}
