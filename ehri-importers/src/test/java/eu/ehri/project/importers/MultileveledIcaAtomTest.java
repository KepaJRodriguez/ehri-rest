/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.ehri.project.importers;

import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.events.SystemEvent;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linda
 */
public class MultileveledIcaAtomTest extends AbstractImporterTest{
    //
    private static final Logger logger = LoggerFactory.getLogger(MultileveledIcaAtomTest.class);
       protected final String MULTI_EAD = "zbirka-gradiva-za-povijest-zidova-collection-of-material-concerning-history-of-jews.xml";

    // Depends on fixtures
    protected final String TEST_REPO = "r1";

    // Depends on single-ead.xml
    protected final String IMPORTED_ITEM_ID = "HR r000382HR HR-HDA 1551";
    private final String UN_REL = "HR-HDA145corporateBodyAccessCroatianStateArchive";

    @Test
    public void testImportItemsT() throws Exception {
        Repository agent = manager.getFrame(TEST_REPO, Repository.class);
        final String logMessage = "Importing a multileveled EAD by MultileveledIcaAtomTest";

        int origCount = getNodeCount(graph);

        InputStream ios = ClassLoader.getSystemResourceAsStream(MULTI_EAD);
        ImportLog log = new SaxImportManager(graph, agent, validUser, IcaAtomEadImporter.class, IcaAtomEadHandler.class).setTolerant(Boolean.TRUE).importFile(ios, logMessage);

//        printGraph(graph);
        // How many new nodes will have been created? We should have
        // - 3 more DocumentaryUnit
        // - 3 more DocumentDescription
        // - 3 more DatePeriod
        //TODO: test these UR's
        // - 4 more UndeterminedRelationships
        //TODO: test this UP
        // - 3 more UnknownProperty
        // - 4 more import Event links
        // - 1 more import Event

        int createCount = origCount + 21;

        // - 4 more UnderterminedRelationship nodes

        assertEquals(createCount, getNodeCount(graph));

        // Yet we've only created 1 *logical* item...
        assertEquals(3, log.getSuccessful());

        Iterable<Vertex> docs = graph.getVertices("identifier", IMPORTED_ITEM_ID);
        assertTrue(docs.iterator().hasNext());
        DocumentaryUnit unit = graph.frame(docs.iterator().next(), DocumentaryUnit.class);
        for(Description d : unit.getDocumentDescriptions())
            assertEquals("Zbirka gradiva za povijest Židova (Collection of material concerning the history of Jews)", d.getName());

        for(SystemEvent event : unit.getLatestEvent()){
            logger.debug("event: " + event.getLogMessage());
        }
        UndeterminedRelationship creator = graph.frame(graph.getVertices("identifier", UN_REL).iterator().next(), UndeterminedRelationship.class);
        for(String key : creator.asVertex().getPropertyKeys()){
            logger.debug(key + ": " + creator.asVertex().getProperty(key));
        }
        assertTrue(creator.asVertex().getPropertyKeys().contains("role"));
        List<SystemEvent> actions = toList(unit.getHistory());
        // Check we've only got one action
        assertEquals(1, actions.size());
        assertEquals(logMessage, actions.get(0).getLogMessage());

        // Now re-import the same file
        InputStream ios2 = ClassLoader.getSystemResourceAsStream(MULTI_EAD);
        ImportLog log2 = new SaxImportManager(graph, agent, validUser, IcaAtomEadImporter.class, IcaAtomEadHandler.class).importFile(ios2, logMessage);

        // We should only the following nodes, for 
        // 1 the action and 
        // 3 the user event links, 
        // 1 plus the global event
        assertEquals(createCount + 5, getNodeCount(graph));
        // And three logical item should've been updated
        assertEquals(3, log2.getUpdated());

    }
}
