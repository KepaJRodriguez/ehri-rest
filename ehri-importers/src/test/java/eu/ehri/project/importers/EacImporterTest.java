package eu.ehri.project.importers;

import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.HistoricalAgent;
import eu.ehri.project.models.HistoricalAgentDescription;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.UnknownProperty;
import eu.ehri.project.models.base.*;
import eu.ehri.project.models.events.SystemEvent;
import java.io.InputStream;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linda
 */
public class EacImporterTest extends AbstractImporterTest {

    private static final Logger logger = LoggerFactory.getLogger(EacImporterTest.class);

    @Test
    public void testAbwehrWithAllReferredNodes() throws Exception {
        final String SINGLE_EAC = "abwehr.xml";
        final String logMessage = "Importing EAC " + SINGLE_EAC + " and creating all two relations with previously created HistoricalAgents";
        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_EAC);
        new SaxImportManager(graph, SystemScope.getInstance(), validUser, EacImporter.class,
                EacHandler.class).setTolerant(Boolean.TRUE).importFile(ClassLoader.getSystemResourceAsStream("geheime-feldpolizei.xml"), logMessage);
        new SaxImportManager(graph, SystemScope.getInstance(), validUser, EacImporter.class,
                EacHandler.class).setTolerant(Boolean.TRUE).importFile(ClassLoader.getSystemResourceAsStream("ss-rasse.xml"), logMessage);
        //import abwehr last, so it will find the other UR's
        ImportLog log = new SaxImportManager(graph, SystemScope.getInstance(), validUser, EacImporter.class,
                EacHandler.class).setTolerant(Boolean.TRUE).importFile(ios, logMessage);
        printGraph(graph);
        HistoricalAgent abwehr = manager.getFrame("381", HistoricalAgent.class);
        logger.debug(abwehr.getId());
        assertEquals(Entities.HISTORICAL_AGENT, abwehr.getType());
        assertTrue(abwehr != null);
        for(Description abwehrDesc : abwehr.getDescriptions()){
            logger.debug(abwehrDesc.getName());
        }
        for(Link a : abwehr.getLinks()){
            logger.info(a.getId() + " has targets: " + toList(a.getLinkTargets()).size());
            for (LinkableEntity e : a.getLinkTargets()){
                logger.debug(e.getType());
            }
            assertEquals(2, toList(a.getLinkBodies()).size());
        }
        assertEquals(2, toList(abwehr.getLinks()).size());

        HistoricalAgent ssrasse = manager.getFrame("418", HistoricalAgent.class);
        logger.debug(ssrasse.getId());
        assertEquals(Entities.HISTORICAL_AGENT, ssrasse.getType());
        assertEquals(1, toList(ssrasse.getLinks()).size());

        HistoricalAgent feldpolizei = manager.getFrame("717", HistoricalAgent.class);
        logger.debug(feldpolizei.getId());
        assertEquals(Entities.HISTORICAL_AGENT, feldpolizei.getType());
        assertEquals(1, toList(feldpolizei.getLinks()).size());
        for(Description polizeiDesc : feldpolizei.getDescriptions()){
            assertEquals("Geheime Feldpolizei", polizeiDesc.getName());
        }

    }

        @Test
    public void testAbwehrWithOUTAllReferredNodes() throws Exception {
        final String SINGLE_EAC = "abwehr.xml";
        final String logMessage = "Importing EAC " + SINGLE_EAC + " without creating any annotation, since the targets are not present in the graph";
        int count = getNodeCount(graph);
        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_EAC);
        ImportLog log = new SaxImportManager(graph, SystemScope.getInstance(), validUser, EacImporter.class,
                EacHandler.class).setTolerant(Boolean.TRUE).importFile(ios, logMessage);
        printGraph(graph);
        /**
         * How many new nodes will have been created? We should have
         * - 1 more Repository
         * - 1 more RepositoryDescription
         * - 1 more UnknownProperty
         * - 2 more MaintenanceEvent
         * - 2 more UnknownRelations 
         * - 2 more linkEvents (1 for the Repository, 1 for the User)
         * - 1 more SystemEvent        
        **/
        assertEquals(count + 10, getNodeCount(graph));
        
        HistoricalAgent abwehr = manager.getFrame("381", HistoricalAgent.class);
        logger.debug(abwehr.getId());
        assertEquals(Entities.HISTORICAL_AGENT, abwehr.getType());
        assertNotNull(abwehr);
        assertEquals(0, toList(abwehr.getAnnotations()).size());
        
        for(Description desc : abwehr.getDescriptions()){
            logger.debug(desc.getLanguageOfDescription());
            for(UnknownProperty u: desc.getUnknownProperties()){
                logger.debug(u.getType());
            }
        }
        
    }

//        @Test
    public void testImportItemsAlgemeyner() throws Exception {
        final String SINGLE_EAC = "algemeyner-yidisher-arbeter-bund-in-lite-polyn-un-rusland.xml";
        // Depends on fixtures
        final String TEST_REPO = "r1";
        // Depends on hierarchical-ead.xml
        final String IMPORTED_ITEM_ID = "159#object";
        final String AUTHORITY_DESC = "159";

        final String logMessage = "Importing EAC " + SINGLE_EAC;

        int count = getNodeCount(graph);
        logger.debug("count of nodes before importing: " + count);

        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_EAC);
        ImportLog log = new SaxImportManager(graph, SystemScope.getInstance(), validUser, EacImporter.class,
                EacHandler.class).setTolerant(Boolean.TRUE).importFile(ios, logMessage);
        printGraph(graph);
        // How many new nodes will have been created? We should have
        // - 1 more HistoricalAgent
        // - 1 more HistoricalAgentDescription
        // - 2 more MaintenanceEvent 
        // - 2 more linkEvents (1 for the HistoricalAgent, 1 for the User)
        // - 1 more SystemEvent
        assertEquals(count + 7, getNodeCount(graph));

            Iterable<Vertex> docs = graph.getVertices(Ontology.IDENTIFIER_KEY,
                    IMPORTED_ITEM_ID);
            assertTrue(docs.iterator().hasNext());
            HistoricalAgent unit = graph.frame(
                    getVertexByIdentifier(graph,IMPORTED_ITEM_ID),
                    HistoricalAgent.class);

        // check the child items
        HistoricalAgentDescription c1 = graph.frame(
                getVertexByIdentifier(graph, AUTHORITY_DESC),
                HistoricalAgentDescription.class);
        assertEquals(Entities.HISTORICAL_AGENT_DESCRIPTION, c1.getType());


        assertTrue(c1.asVertex().getProperty(Ontology.NAME_KEY) instanceof String);
        assertTrue(c1.asVertex().getProperty("otherFormsOfName") instanceof String);

        // Ensure that c1 is a description of the unit
        for (Description d : unit.getDescriptions()) {

            assertEquals(d.getName(), c1.getName());
                assertEquals(d.getEntity().getId(), unit.getId());
            }

//TODO: find out why the unit and the action are not connected ...
//            Iterable<Action> actions = unit.getHistory();
//            assertEquals(1, toList(actions).size());
        // Check we've only got one action
        assertEquals(1, log.getCreated());
        assertTrue(log.getAction() instanceof SystemEvent);
        assertEquals(logMessage, log.getAction().getLogMessage());

        // Ensure the import action has the right number of subjects.
        List<AccessibleEntity> subjects = toList(log.getAction().getSubjects());
        assertEquals(1, subjects.size());
        assertEquals(log.getChanged(), subjects.size());


//        System.out.println("created: " + log.getCreated());

    }
}
