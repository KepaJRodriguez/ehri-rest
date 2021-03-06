/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.ehri.project.importers;

import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.Country;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.RepositoryDescription;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.events.SystemEvent;

import java.io.InputStream;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;

/**
 * @author linda
 */
public class Eag2896Test extends AbstractImporterTest {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Eag2896Test.class);
    protected final String SINGLE_UNIT = "eag-2896.xml";
    // Depends on fixtures
    protected final String TEST_COUNTRY = "r1";
    // Depends on SINGLE_UNIT
    protected final String IMPORTED_ITEM_ID = "NL-002896";
    protected final String AGENT_DESC_ID = "NL-002896#desc";

    @Test
    public void testImportItemsT() throws Exception {
        Country country = manager.getFrame(TEST_COUNTRY, Country.class);
        final String logMessage = "Importing a single EAG";

        int count = getNodeCount(graph);
        logger.info("count of nodes before importing: " + count);

        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_UNIT);
        SaxImportManager importManager = new SaxImportManager(graph, country, validUser, EagImporter.class, EagHandler.class);
        ImportLog log = importManager.importFile(ios, logMessage);
        //printGraph(graph);
        // How many new nodes will have been created? We should have
        // - 1 more Repository
        // - 1 more RepositoryDescription
        // - 1 more Address
        // - 1 more UnknownProperty
        // - 2 more MaintenanceEvent
        // - 2 more linkEvents (1 for the Repository, 1 for the User)
        // - 1 more SystemEvent

        int afterCount = count + 9;
        assertEquals(afterCount, getNodeCount(graph));

        Iterable<Vertex> docs = graph.getVertices(Ontology.IDENTIFIER_KEY, IMPORTED_ITEM_ID);
        assertTrue(docs.iterator().hasNext());
        Repository unit = graph.frame(getVertexByIdentifier(graph, IMPORTED_ITEM_ID), Repository.class);
        assertEquals(Entities.REPOSITORY, unit.getType());

        // check the child items
        RepositoryDescription c1 = graph.frame(getVertexByIdentifier(graph, AGENT_DESC_ID), RepositoryDescription.class);
        assertEquals(Entities.REPOSITORY_DESCRIPTION, c1.getType());
        Object notes = c1.asVertex().getProperty(EagImporter.MAINTENANCE_NOTES);
        if (notes instanceof String[]) {
            fail("Maintenance notes property should not be an array");
        } else {
            assertTrue(notes instanceof String);
        }

        // MB: Test priority hack - this should be pulled out of the
        // maintenanceNotes field into its own int field
        Object priority = unit.asVertex().getProperty(EagImporter.PRIORITY);
        assertEquals(Integer.valueOf(5), priority);

        // Check scope
        assertEquals(country, unit.getPermissionScope());

        //check whether the description has an Address attached to it
        assertEquals(1, toList(c1.getAddresses()).size());

        assertEquals(2, toList(c1.getMaintenanceEvents()).size());
        // Ensure that c1 is a description of the unit
        for (Description d : unit.getDescriptions()) {
            assertEquals(IMPORTED_ITEM_ID, d.getDescribedEntity().getIdentifier());
        }

        // Check we've only got one action
        assertEquals(1, log.getCreated());
        assertTrue(log.getAction() instanceof SystemEvent);
        assertEquals(logMessage, log.getAction().getLogMessage());

        // Ensure the import action has the right number of subjects.
        List<AccessibleEntity> subjects = toList(log.getAction().getSubjects());
        assertEquals(1, subjects.size());
        assertEquals(log.getChanged(), subjects.size());

        // Test idempotency
        int edgeCount = getEdgeCount(graph);
        ImportLog log2 = importManager.importFile(ClassLoader.getSystemResourceAsStream(SINGLE_UNIT), logMessage);
        assertFalse(log2.hasDoneWork());
        assertEquals(afterCount, getNodeCount(graph));
        assertEquals(edgeCount, getEdgeCount(graph));
    }
}
