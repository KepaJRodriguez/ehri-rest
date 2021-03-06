package eu.ehri.project.views;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.pipes.branch.LoopPipe;
import com.tinkerpop.pipes.util.Pipeline;
import eu.ehri.project.acl.AclManager;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.VirtualUnit;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.utils.JavaHandlerUtils;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class VirtualUnitViews {

    private final FramedGraph<?> graph;
    private final GraphManager manager;
    private final AclManager aclManager;

    public VirtualUnitViews(FramedGraph<?> graph) {
        this.graph = graph;
        this.manager = GraphManagerFactory.getInstance(graph);
        this.aclManager = new AclManager(graph);
    }

    /**
     * Find virtual collections to which this item belongs.
     *
     * @param item     An item (typically a documentary unit)
     * @param accessor The current user
     * @return A set of top-level virtual units
     */
    public Iterable<VirtualUnit> getVirtualCollections(Frame item, Accessor accessor) {

        // This is a relatively complicated traversal. We want to go from the item,
        // then to any descriptions, from those descriptions to any virtual units
        // that reference them, and then up to the top-level item. It is complicated
        // by the fact that the first encountered unit might actually be the top-level
        // item, in which case our loop traversal will miss it, so we have to combine
        // the result

        GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
        Pipeline<Vertex, Vertex> otherPipe = pipe.start(item.asVertex())
                .in(Ontology.DESCRIPTION_FOR_ENTITY)
                .in(Ontology.VC_DESCRIBED_BY)
                .as("n").out(Ontology.VC_IS_PART_OF)
                .loop("n", JavaHandlerUtils.defaultMaxLoops, new PipeFunction<LoopPipe.LoopBundle<Vertex>, Boolean>() {
                    @Override
                    public Boolean compute(LoopPipe.LoopBundle<Vertex> vertexLoopBundle) {
                        return (!vertexLoopBundle.getObject()
                                .getEdges(Direction.OUT, Ontology.VC_IS_PART_OF)
                                .iterator().hasNext())
                                && manager.getEntityClass(vertexLoopBundle.getObject())
                                .equals(EntityClass.VIRTUAL_UNIT);
                    }
                });

        GremlinPipeline<Vertex, Vertex> out = new GremlinPipeline<Vertex, Vertex>(item.asVertex())
                .copySplit(new GremlinPipeline<Vertex, Object>(item.asVertex())
                        .in(Ontology.DESCRIPTION_FOR_ENTITY)
                        .in(Ontology.VC_DESCRIBED_BY), otherPipe)
                .exhaustMerge().cast(Vertex.class).filter(new PipeFunction<Vertex, Boolean>() {
                    @Override
                    public Boolean compute(Vertex vertex) {
                        return !vertex.getEdges(Direction.OUT, Ontology.VC_IS_PART_OF)
                                .iterator().hasNext();
                    }
                }).filter(aclManager.getAclFilterFunction(accessor));

        return graph.frameVertices(out, VirtualUnit.class);
    }

    /**
     * Find virtual collections to which this user belongs.
     *
     * @param user     An user (typically a documentary unit)
     * @param accessor The current user
     * @return A set of top-level virtual units
     */
    public Iterable<VirtualUnit> getVirtualCollectionsForUser(Frame user, Accessor accessor) {
        GremlinPipeline<Vertex, Vertex> pipe = new GremlinPipeline<Vertex, Vertex>();
        Pipeline<Vertex, Vertex> filtered = pipe.start(user.asVertex())
                .in(Ontology.VC_HAS_AUTHOR)
                .filter(aclManager.getAclFilterFunction(accessor));

        return graph.frameVertices(filtered, VirtualUnit.class);
    }
}
