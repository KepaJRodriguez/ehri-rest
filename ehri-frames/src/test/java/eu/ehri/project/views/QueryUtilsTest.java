package eu.ehri.project.views;

import com.google.common.base.Optional;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: michaelb
 * Date: 30/01/13
 * Time: 15:12
 * To change this template use File | Settings | File Templates.
 */
public class QueryUtilsTest {

    @Test
    public void testGetTraversalPath() throws Exception {
        String notAPath = "imNotAPath";
        Optional<QueryUtils.TraversalPath> traversalPath = QueryUtils.getTraversalPath(notAPath);
        assertFalse(traversalPath.isPresent());

        String validPath = "->foo<-bar.baz";
        traversalPath = QueryUtils.getTraversalPath(validPath);
        assertTrue(traversalPath.isPresent());

        String badPath = "foo->bar.baz";
        traversalPath = QueryUtils.getTraversalPath(badPath);
        assertFalse(traversalPath.isPresent());
    }
}
