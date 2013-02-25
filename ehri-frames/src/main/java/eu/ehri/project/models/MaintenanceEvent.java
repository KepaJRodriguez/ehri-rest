/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.TemporalEntity;

/**
 *
 * @author linda
 */
@EntityType(EntityClass.MAINTENANCE_EVENT)
public interface MaintenanceEvent extends TemporalEntity, AccessibleEntity{
     public static final String EVENTTYPE = "eventType";
     public static final String AGENTTYPE = "agentType";
     public enum EventType { CREATED, REVISED }
     public enum AgentType { HUMAN }
     
    //TODO: decide whether to make these required 
    @Property(EVENTTYPE)
    public void setEventType(EventType eventType);

    @Property(AGENTTYPE)
    public void setAgentType(AgentType agentType);

    //not required
    @Adjacency(label = Authority.CREATED, direction = Direction.IN)
    public Iterable<Authority> getCreators();

    @Adjacency(label = Authority.CREATED, direction = Direction.IN)
    public void addCreator(final Authority creator);
}