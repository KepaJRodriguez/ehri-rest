package eu.ehri.project.importers;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.IdGenerationError;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.Agent;
import eu.ehri.project.models.DatePeriod;
import eu.ehri.project.models.DocumentDescription;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityTypes;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.TemporalEntity;
import eu.ehri.project.models.idgen.DocumentaryUnitIdGenerator;
import eu.ehri.project.persistance.BundleDAO;
import eu.ehri.project.persistance.BundleFactory;
import eu.ehri.project.persistance.EntityBundle;

/**
 * Base class for importers that import documentary units, with their
 * constituent logical data, description(s), and date periods.
 * 
 * @author michaelb
 * 
 * @param <T>
 */
public abstract class AbstractImporter<T> {

    protected final Agent repository;
    protected final FramedGraph<Neo4jGraph> framedGraph;
    protected final GraphManager manager;
    protected final ImportLog log;
    protected final T documentContext;
    private List<ImportCallback> createCallbacks = new LinkedList<ImportCallback>();
    private List<ImportCallback> updateCallbacks = new LinkedList<ImportCallback>();

    /**
     * Constructor.
     * 
     * @param framedGraph
     * @param repository
     * @param log
     * @param documentContext
     */
    public AbstractImporter(FramedGraph<Neo4jGraph> framedGraph,
            Agent repository, ImportLog log, T documentContext) {
        this.repository = repository;
        this.framedGraph = framedGraph;
        this.log = log;
        this.documentContext = documentContext;
        manager = GraphManagerFactory.getInstance(framedGraph);
    }

    /**
     * Add a callback to run when an item is created.
     * 
     * @param cb
     */
    public void addCreationCallback(final ImportCallback cb) {
        createCallbacks.add(cb);
    }

    /**
     * Add a callback to run when an item is updated.
     * 
     * @param cb
     */
    public void addUpdateCallback(final ImportCallback cb) {
        updateCallbacks.add(cb);
    }

    /**
     * Extract the logical DocumentaryUnit at a given depth.
     * 
     * @param itemData
     * @param depth
     * @return
     * @throws ValidationError
     */
    protected abstract Map<String, Object> extractDocumentaryUnit(T itemData,
            int depth) throws ValidationError;

    /**
     * Extract DocumentDescriptions at a given depth from the input data.
     * 
     * @param itemData
     * @param depth
     * @return
     * @throws ValidationError
     */
    protected abstract Iterable<Map<String, Object>> extractDocumentDescriptions(
            T itemData, int depth) throws ValidationError;

    /**
     * Extract a list of DatePeriod bundles from an item's data.
     * 
     * @param data
     * @return
     */
    public abstract Iterable<Map<String, Object>> extractDates(T data);

    /**
     * Import a single archdesc or c01-12 item, keeping a reference to the
     * hierarchical depth.
     * 
     * @param itemData
     * @param parent
     * @param depth
     * @throws ValidationError
     * @throws IntegrityError
     */
    protected DocumentaryUnit importItem(T itemData, DocumentaryUnit parent,
            int depth) throws ValidationError, IntegrityError {
        EntityBundle<DocumentaryUnit> unit = new BundleFactory<DocumentaryUnit>()
                .buildBundle(extractDocumentaryUnit(itemData, depth),
                        DocumentaryUnit.class);
        BundleDAO<DocumentaryUnit> persister = new BundleDAO<DocumentaryUnit>(
                framedGraph, repository);

        // Add dates and descriptions to the bundle since they're @Dependent
        // relations.
        for (Map<String, Object> dpb : extractDates(itemData)) {
            unit.addRelation(TemporalEntity.HAS_DATE,
                    new BundleFactory<DatePeriod>().buildBundle(dpb,
                            DatePeriod.class));
        }
        for (Map<String, Object> dpb : extractDocumentDescriptions(itemData,
                depth)) {
            unit.addRelation(Description.DESCRIBES,
                    new BundleFactory<DocumentDescription>().buildBundle(dpb,
                            DocumentDescription.class));
        }

        DocumentaryUnitIdGenerator generator = new DocumentaryUnitIdGenerator();
        String id = null;
        try {
            id = generator.generateId(EntityTypes.DOCUMENTARY_UNIT, repository,
                    unit.getData());
        } catch (IdGenerationError e) {
            throw new ValidationError("Bad data: " + unit.getData());
        }
        boolean exists = manager.exists(id);
        System.out.println("IMPORTING: " + unit.getData());
        DocumentaryUnit frame = persister
                .createOrUpdate(new EntityBundle<DocumentaryUnit>(id, unit
                        .getData(), unit.getBundleClass(), unit.getRelations()));

        // Set the repository/item relationship
        repository.addCollection(frame);
        // Set the parent child relationship
        if (parent != null)
            parent.addChild(frame);

        // Run creation callbacks for the new item...
        if (exists) {
            for (ImportCallback cb : updateCallbacks) {
                cb.itemImported(frame);
            }
        } else {
            for (ImportCallback cb : createCallbacks) {
                cb.itemImported(frame);
            }
        }
        return frame;
    }
}