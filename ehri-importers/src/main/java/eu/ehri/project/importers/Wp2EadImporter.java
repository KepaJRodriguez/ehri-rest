/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.ehri.project.importers;

import com.google.common.collect.Sets;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.impl.CrudViews;

import java.util.Map;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linda
 */
public class Wp2EadImporter extends IcaAtomEadImporter {

    private static final Logger logger = LoggerFactory.getLogger(Wp2EadImporter.class);
    private final Accessor userProfile;
    public static final String WP2AUTHOR = "EHRI - Terezin Research Guide";
    public static final String PROPERTY_AUTHOR = "authors";

    public Wp2EadImporter(FramedGraph<Neo4jGraph> framedGraph, PermissionScope permissionScope, ImportLog log) {
        super(framedGraph, permissionScope, log);
        try {
            userProfile = manager.getFrame(log.getActioner().getId(), Accessor.class);
        } catch (ItemNotFound ex) {
            throw new RuntimeException("Unable to find accessor with given id: " + log.getActioner().getId());
        }
    }
    
    @Override
    protected Map<String, Object> extractUnitDescription(Map<String, Object> itemData, EntityClass entity) {
        Map<String, Object> map = super.extractUnitDescription(itemData, entity);
        map.put(PROPERTY_AUTHOR, WP2AUTHOR);    
        return map;
    }

    /**
     * Tries to resolve the undetermined relationships for Wp2 ead files by iterating through all
     * UndeterminedRelationships, finding the DescribedEntity meant by the 'targetUrl' in the Relationship and creating
     * an Annotation for it.
     *
     *
     * @param unit
     * @param descBundle
     * @throws ValidationError
     */
    @Override
    protected void solveUndeterminedRelationships(DocumentaryUnit unit, Bundle descBundle) throws ValidationError {
        //Try to resolve the undetermined relationships
        //we can only create the annotations after the DocumentaryUnit and its Description have been added to the graph,
        //so they have id's. 
        for (Description unitdesc : unit.getDescriptions()) {

            // Put the set of relationships into a HashSet to remove duplicates.
            for (UndeterminedRelationship rel : Sets.newHashSet(unitdesc.getUndeterminedRelationships())) {
                /*
                 * the wp2 undetermined relationship that can be resolved have a 'cvoc' and a 'concept' attribute.
                 * they need to be found in the vocabularies that are in the graph
                 */
                for (String property : rel.asVertex().getPropertyKeys()) {
                    logger.debug(property);
                }
                if (rel.asVertex().getPropertyKeys().contains("cvoc")) {
                    String cvoc_id = (String) rel.asVertex().getProperty("cvoc");
                    String concept_id = (String) rel.asVertex().getProperty("concept");
                    logger.debug(cvoc_id + "  " + concept_id);
                    Vocabulary vocabulary;
                    try {
                        vocabulary = manager.getFrame(cvoc_id, Vocabulary.class);
                        for (Concept concept : vocabulary.getConcepts()) {
                        logger.debug("*********************" + concept.getId() + " " + concept.getIdentifier());
                        if (concept.getIdentifier().equals(concept_id)) {
                            try {
                                Bundle linkBundle = new Bundle(EntityClass.LINK)
                                        .withDataValue(Ontology.LINK_HAS_TYPE, "resolved relationship")
                                        .withDataValue(Ontology.LINK_HAS_DESCRIPTION, "solved by automatic resolving");
                                Link link = new CrudViews<Link>(framedGraph, Link.class).create(linkBundle, userProfile);
                                unit.addLink(link);
                                concept.addLink(link);
                                link.addLinkBody(rel);
                            } catch (PermissionDenied ex) {
                                java.util.logging.Logger.getLogger(Wp2EadImporter.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (IntegrityError ex) {
                                java.util.logging.Logger.getLogger(Wp2EadImporter.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        }

                    }
                    } catch (ItemNotFound ex) {
                        logger.error("Vocabulary with id " + cvoc_id +" not found. "+ex.getMessage());
                    }
                    
                }
            }
        }
    }
}
